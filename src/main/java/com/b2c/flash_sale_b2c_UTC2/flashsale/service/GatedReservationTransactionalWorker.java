package com.b2c.flash_sale_b2c_UTC2.flashsale.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.CreateReservationRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.ReservationResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import com.b2c.flash_sale_b2c_UTC2.flashsale.exception.FlashSaleErrorCode;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.CreateFlashSaleOrderCommand;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.FlashSaleOrderPort;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.OrderRef;
import com.b2c.flash_sale_b2c_UTC2.flashsale.realtime.FlashSaleWsBroadcaster;
import com.b2c.flash_sale_b2c_UTC2.flashsale.realtime.FlashSaleWsEvent;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.user.entity.Address;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class GatedReservationTransactionalWorker {

    private final FlashSaleItemRepository itemRepository;
    private final FlashSaleOrderPort orderPort;
    private final FlashSaleWsBroadcaster wsBroadcaster;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    public static final String PENDING_RES_PREFIX = "flash_sale:pending_res:";
    public static final String PENDING_RES_INDEX = "flash_sale:pending_res_index";

    @Transactional
    public ReservationResponse executeGatedDatabaseReservation(
            Long userId,
            Address address,
            FlashSaleItem item,
            FlashSaleSlot slot,
            CreateReservationRequest request,
            String orderCode,
            String idemKey
    ) {
        if (orderPort == null) {
            log.error("FlashSaleOrderPort is not configured!");
            throw new BusinessException(FlashSaleErrorCode.ORDER_CREATION_FAILED);
        }

        Instant expiresAt = Instant.now().plusSeconds(slot.getReservationTtlSeconds());
        BigDecimal totalAmount = item.getFlashSalePrice().multiply(BigDecimal.valueOf(request.getQuantity()));

        CreateFlashSaleOrderCommand cmd = new CreateFlashSaleOrderCommand(
                userId,
                address.getId(),
                item.getId(),
                slot.getId(),
                item.getVariant().getId(),
                item.getVariant().getProduct().getStore().getId(),
                request.getQuantity(),
                item.getFlashSalePrice(),
                item.getVariant().getProduct().getName(),
                item.getVariant().getVariantName(),
                orderCode,
                expiresAt
        );

        // Tạo order trước
        OrderRef orderRef = orderPort.createPendingOrder(cmd);

        // Sau cùng mới UPDATE hàng DB để thời gian giữ row-lock ngắn nhất
        int dbUpdated = itemRepository.deductAvailableStockConditionally(item.getId(), request.getQuantity());
        if (dbUpdated == 0) {
            log.warn("DB available_stock was insufficient for itemId: {}. Forcing rollback.", item.getId());
            throw new BusinessException(FlashSaleErrorCode.OUT_OF_STOCK);
        }

        // Dọn dẹp pending res trên Redis & gán cache idempotency
        if (redisTemplate != null) {
            redisTemplate.delete(PENDING_RES_PREFIX + orderCode);
            redisTemplate.opsForZSet().remove(PENDING_RES_INDEX, orderCode);
            if (idemKey != null) {
                redisTemplate.opsForValue().set(idemKey, orderRef.orderCode(), Duration.ofMinutes(10));
            }
        }

        // Đẩy WebSocket ra sau khi transaction đã commit thành công
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        wsBroadcaster.broadcastStockUpdate(slot.getId(), item.getId(), item.getAvailableStock() - request.getQuantity());
                        User buyer = (userRepository != null) ? userRepository.findById(userId).orElse(null) : null;
                        if (buyer != null) {
                            FlashSaleWsEvent event = FlashSaleWsEvent.builder()
                                    .eventType(FlashSaleWsEvent.EventType.ORDER_RESERVED)
                                    .slotId(slot.getId())
                                    .flashSaleItemId(item.getId())
                                    .orderId(orderRef.orderId())
                                    .orderCode(orderRef.orderCode())
                                    .quantity(request.getQuantity())
                                    .totalAmount(totalAmount.longValue())
                                    .expiresAt(expiresAt)
                                    .occurredAt(Instant.now())
                                    .build();
                            wsBroadcaster.sendReservationResultToUser(buyer.getEmail(), event);
                        }
                    } catch (Exception wsEx) {
                        log.warn("[WS] afterCommit broadcast warning: {}", wsEx.getMessage());
                    }
                }
            });
        }

        return ReservationResponse.builder()
                .orderId(orderRef.orderId())
                .orderCode(orderRef.orderCode())
                .reservationId(orderRef.orderCode())
                .flashSaleItemId(item.getId())
                .quantity(request.getQuantity())
                .flashSalePrice(item.getFlashSalePrice())
                .totalAmount(totalAmount)
                .status("PENDING_PAYMENT")
                .expiresAt(expiresAt)
                .message("Giữ chỗ Flash Sale thành công. Vui lòng thanh toán trước thời hạn.")
                .build();
    }
}
