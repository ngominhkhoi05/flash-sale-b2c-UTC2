package com.b2c.flash_sale_b2c_UTC2.flashsale.service;

import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.FlashSaleOrderStreamEvent;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.CreateFlashSaleOrderCommand;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.FlashSaleOrderPort;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.OrderRef;
import com.b2c.flash_sale_b2c_UTC2.flashsale.realtime.FlashSaleWsBroadcaster;
import com.b2c.flash_sale_b2c_UTC2.flashsale.realtime.FlashSaleWsEvent;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.strategy.StockReservationStrategy;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleReservationService.IDEMPOTENCY_KEY_PREFIX;
import static com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleReservationService.PENDING_RES_INDEX;
import static com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleReservationService.PENDING_RES_PREFIX;

@Slf4j
@Service
public class FlashSaleOrderStreamConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    public static final String STREAM_KEY = "flash_sale:order_stream";
    public static final String GROUP_NAME = "flash_sale_order_group";

    private final FlashSaleItemRepository itemRepository;
    private final FlashSaleOrderPort orderPort;
    private final StockReservationStrategy stockReservationStrategy;
    private final FlashSaleWsBroadcaster wsBroadcaster;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public FlashSaleOrderStreamConsumer(
            FlashSaleItemRepository itemRepository,
            FlashSaleOrderPort orderPort,
            StockReservationStrategy stockReservationStrategy,
            FlashSaleWsBroadcaster wsBroadcaster,
            @Autowired(required = false) UserRepository userRepository,
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper
    ) {
        this.itemRepository = itemRepository;
        this.orderPort = orderPort;
        this.stockReservationStrategy = stockReservationStrategy;
        this.wsBroadcaster = wsBroadcaster;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        RecordId recordId = message.getId();
        Map<String, String> body = message.getValue();

        String payloadJson = body.get("payload");
        if (payloadJson == null) {
            log.warn("[STREAM_CONSUMER] Received empty payload for record: {}", recordId);
            acknowledge(recordId);
            return;
        }

        FlashSaleOrderStreamEvent event;
        try {
            event = objectMapper.readValue(payloadJson, FlashSaleOrderStreamEvent.class);
        } catch (Exception e) {
            log.error("[STREAM_CONSUMER] Failed to deserialize event: {}", payloadJson, e);
            acknowledge(recordId);
            return;
        }

        try {
            processOrderEvent(event);
            acknowledge(recordId);
        } catch (Exception e) {
            log.error("[STREAM_CONSUMER] Error processing order event for orderCode={}: {}", event.getOrderCode(), e.getMessage(), e);
            handleCompensation(event);
            acknowledge(recordId); // ACK sau khi compensation để không bị loop vô hạn
        }
    }

    private void processOrderEvent(FlashSaleOrderStreamEvent event) {
        log.info("[STREAM_CONSUMER] Processing order: orderCode={}, itemId={}, qty={}",
                event.getOrderCode(), event.getFlashSaleItemId(), event.getQuantity());

        OrderRef orderRef = transactionTemplate.execute(status -> {
            // 1. UPDATE available_stock DB có điều kiện
            int updated = itemRepository.deductAvailableStockConditionally(event.getFlashSaleItemId(), event.getQuantity());
            if (updated == 0) {
                log.warn("[STREAM_CONSUMER] DB stock insufficient for itemId: {}", event.getFlashSaleItemId());
                throw new IllegalStateException("DB available_stock insufficient");
            }

            // 2. Tạo đơn PENDING_PAYMENT
            CreateFlashSaleOrderCommand cmd = new CreateFlashSaleOrderCommand(
                    event.getUserId(),
                    event.getAddressId(),
                    event.getFlashSaleItemId(),
                    event.getSlotId(),
                    event.getVariantId(),
                    event.getStoreId(),
                    event.getQuantity(),
                    event.getUnitPrice(),
                    event.getProductName(),
                    event.getVariantName(),
                    event.getOrderCode(),
                    event.getExpiresAt()
            );

            return orderPort.createPendingOrder(cmd);
        });

        if (orderRef == null) {
            throw new IllegalStateException("Failed to create pending order in DB");
        }

        // 3. Sau commit thành công: Dọn dẹp bản ghi tạm Redis và cập nhật Idempotency
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(PENDING_RES_PREFIX + event.getOrderCode());
                redisTemplate.opsForZSet().remove(PENDING_RES_INDEX, event.getOrderCode());
                if (event.getIdempotencyKey() != null && !event.getIdempotencyKey().isBlank()) {
                    String idemKey = IDEMPOTENCY_KEY_PREFIX + event.getUserId() + ":" + event.getIdempotencyKey();
                    redisTemplate.opsForValue().set(idemKey, orderRef.orderCode(), Duration.ofMinutes(10));
                }
            } catch (Exception redisEx) {
                log.warn("[STREAM_CONSUMER] Error updating Redis state after order created: {}", redisEx.getMessage());
            }
        }

        // 4. Broadcast WebSocket ORDER_RESERVED tới người dùng
        try {
            User buyer = (userRepository != null) ? userRepository.findById(event.getUserId()).orElse(null) : null;
            if (buyer != null) {
                BigDecimal totalAmount = event.getUnitPrice().multiply(BigDecimal.valueOf(event.getQuantity()));
                FlashSaleWsEvent wsEvent = FlashSaleWsEvent.builder()
                        .eventType(FlashSaleWsEvent.EventType.ORDER_RESERVED)
                        .slotId(event.getSlotId())
                        .flashSaleItemId(event.getFlashSaleItemId())
                        .orderId(orderRef.orderId())
                        .orderCode(orderRef.orderCode())
                        .quantity(event.getQuantity())
                        .totalAmount(totalAmount.longValue())
                        .expiresAt(event.getExpiresAt())
                        .occurredAt(Instant.now())
                        .build();
                wsBroadcaster.sendReservationResultToUser(buyer.getEmail(), wsEvent);
            }
        } catch (Exception wsEx) {
            log.warn("[STREAM_CONSUMER] Failed to broadcast WebSocket event: {}", wsEx.getMessage());
        }
    }

    private void handleCompensation(FlashSaleOrderStreamEvent event) {
        log.warn("[STREAM_CONSUMER] Compensating Redis stock for orderCode={}, itemId={}, qty={}",
                event.getOrderCode(), event.getFlashSaleItemId(), event.getQuantity());
        try {
            stockReservationStrategy.rollbackStockWithMarker(
                    event.getSlotId(),
                    event.getUserId(),
                    event.getFlashSaleItemId(),
                    event.getQuantity(),
                    event.getOrderCode()
            );

            if (redisTemplate != null && event.getIdempotencyKey() != null) {
                String idemKey = IDEMPOTENCY_KEY_PREFIX + event.getUserId() + ":" + event.getIdempotencyKey();
                redisTemplate.delete(idemKey);
            }
        } catch (Exception compEx) {
            log.error("[STREAM_CONSUMER] Compensation failed for orderCode={}: {}", event.getOrderCode(), compEx.getMessage(), compEx);
        }
    }

    private void acknowledge(RecordId recordId) {
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, recordId);
            } catch (Exception e) {
                log.warn("[STREAM_CONSUMER] Failed to acknowledge message {}: {}", recordId, e.getMessage());
            }
        }
    }
}
