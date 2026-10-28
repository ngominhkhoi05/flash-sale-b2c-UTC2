package com.b2c.flash_sale_b2c_UTC2.flashsale.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.CancelReservationResponse;
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
import com.b2c.flash_sale_b2c_UTC2.flashsale.strategy.StockReservationStrategy;
import com.b2c.flash_sale_b2c_UTC2.order.entity.Order;
import com.b2c.flash_sale_b2c_UTC2.order.entity.OrderItem;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderItemRepository;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderRepository;
import com.b2c.flash_sale_b2c_UTC2.user.entity.Address;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.repository.AddressRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import com.b2c.flash_sale_b2c_UTC2.voucher.service.VoucherService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class FlashSaleReservationService {

    private final FlashSaleItemRepository itemRepository;
    private final AddressRepository addressRepository;
    private final StockReservationStrategy stockReservationStrategy;
    private final FlashSaleWsBroadcaster wsBroadcaster;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private FlashSaleOrderPort orderPort;

    @Autowired(required = false)
    private OrderRepository orderRepository;

    @Autowired(required = false)
    private OrderItemRepository orderItemRepository;

    @Autowired(required = false)
    private UserRepository userRepository;

    @Autowired(required = false)
    private VoucherService voucherService;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    public static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:reservation:";
    public static final String PENDING_RES_PREFIX = "flash_sale:pending_res:";
    public static final String PENDING_RES_INDEX = "flash_sale:pending_res_index";

    @Autowired
    public FlashSaleReservationService(
            FlashSaleItemRepository itemRepository,
            AddressRepository addressRepository,
            StockReservationStrategy stockReservationStrategy,
            FlashSaleWsBroadcaster wsBroadcaster) {
        this.itemRepository = itemRepository;
        this.addressRepository = addressRepository;
        this.stockReservationStrategy = stockReservationStrategy;
        this.wsBroadcaster = wsBroadcaster;
    }

    @Transactional
    public ReservationResponse createReservation(Long userId, String idempotencyKey, CreateReservationRequest request) {
        String idemKey = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank() && redisTemplate != null) {
            idemKey = IDEMPOTENCY_KEY_PREFIX + userId + ":" + idempotencyKey;
            Boolean isFirstReq = redisTemplate.opsForValue().setIfAbsent(idemKey, "PROCESSING", Duration.ofMinutes(10));
            if (Boolean.FALSE.equals(isFirstReq)) {
                String existingVal = redisTemplate.opsForValue().get(idemKey);
                if ("PROCESSING".equals(existingVal)) {
                    log.warn("Duplicate reservation request in flight for userId: {}, idempotencyKey: {}", userId, idempotencyKey);
                    throw new BusinessException(FlashSaleErrorCode.IDEMPOTENCY_CONFLICT);
                }
                if (existingVal != null && !existingVal.isBlank()) {
                    log.info("Returning cached order result for idempotencyKey: {} -> {}", idempotencyKey, existingVal);
                    return ReservationResponse.builder()
                            .orderCode(existingVal)
                            .reservationId(existingVal)
                            .status("PENDING_PAYMENT")
                            .message("Yêu cầu đã được xử lý thành công trước đó (Idempotent replay).")
                            .build();
                }
            }
        }

        // 1. Kiểm tra địa chỉ nhận hàng (Phương án a: bắt buộc có địa chỉ)
        Address address;
        if (request.getAddressId() != null) {
            address = addressRepository.findById(request.getAddressId())
                    .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.ADDRESS_NOT_FOUND_OR_NOT_OWNED));
            if (address.getUser() == null || !address.getUser().getId().equals(userId)) {
                throw new BusinessException(FlashSaleErrorCode.ADDRESS_NOT_FOUND_OR_NOT_OWNED);
            }
        } else {
            // Tự động tìm địa chỉ mặc định, hoặc địa chỉ đầu tiên của người dùng
            address = addressRepository.findByUserIdAndIsDefaultTrue(userId)
                    .orElseGet(() -> {
                        List<Address> userAddresses = addressRepository.findByUserId(userId);
                        return (userAddresses != null && !userAddresses.isEmpty()) ? userAddresses.get(0) : null;
                    });

            if (address == null) {
                if (idemKey != null && redisTemplate != null) redisTemplate.delete(idemKey);
                throw new BusinessException(FlashSaleErrorCode.ADDRESS_REQUIRED);
            }
        }

        FlashSaleItem item = itemRepository.findById(request.getFlashSaleItemId())
                .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.ITEM_NOT_FOUND));

        if (!"APPROVED".equals(item.getStatus())) {
            throw new BusinessException(FlashSaleErrorCode.ITEM_NOT_FOUND);
        }

        FlashSaleSlot slot = item.getSlot();
        Instant now = Instant.now();

        // Strict time check: reserve after end_time or before start_time must be rejected
        if (now.isBefore(slot.getStartTime()) || now.isAfter(slot.getEndTime()) || !"ACTIVE".equals(slot.getStatus())) {
            if (idemKey != null && redisTemplate != null) redisTemplate.delete(idemKey);
            throw new BusinessException(FlashSaleErrorCode.SLOT_NOT_ACTIVE);
        }

        // Fast fail-safe: check Redis emergency pause flag
        if (redisTemplate != null) {
            try {
                String isPaused = redisTemplate.opsForValue().get("flash_sale:slot:" + slot.getId() + ":paused");
                if ("1".equals(isPaused)) {
                    if (idemKey != null) redisTemplate.delete(idemKey);
                    throw new BusinessException(FlashSaleErrorCode.SLOT_NOT_ACTIVE);
                }
            } catch (BusinessException be) {
                throw be;
            } catch (Exception ignored) {}
        }


        long remainingSlotSeconds = Duration.between(now, slot.getEndTime()).getSeconds();
        if (remainingSlotSeconds <= 0) {
            throw new BusinessException(FlashSaleErrorCode.SLOT_ALREADY_ENDED);
        }

        // Sinh mã orderCode trước để truyền vào Lua script ghi bản ghi giữ chỗ tạm
        String orderCode = "FS-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 9000 + 1000);

        boolean redisDeducted = false;
        boolean dbDeducted = false;

        long reservationResult = stockReservationStrategy.reserveStock(
                slot.getId(),
                userId,
                item.getId(),
                request.getQuantity(),
                item.getUserPurchaseLimit(),
                remainingSlotSeconds,
                orderCode
        );

        if (reservationResult == -1) {
            if (idemKey != null && redisTemplate != null) redisTemplate.delete(idemKey);
            throw new BusinessException(FlashSaleErrorCode.PURCHASE_LIMIT_EXCEEDED);
        }
        if (reservationResult == 0) {
            if (idemKey != null && redisTemplate != null) redisTemplate.delete(idemKey);
            throw new BusinessException(FlashSaleErrorCode.OUT_OF_STOCK);
        }

        redisDeducted = true;

        try {
            int dbUpdated = itemRepository.deductAvailableStockConditionally(item.getId(), request.getQuantity());
            if (dbUpdated == 0) {
                log.warn("DB available_stock was insufficient for FlashSaleItem ID: {}. Triggering Redis rollback.", item.getId());
                stockReservationStrategy.rollbackStockWithMarker(slot.getId(), userId, item.getId(), request.getQuantity(), orderCode);
                redisDeducted = false;
                if (idemKey != null && redisTemplate != null) redisTemplate.delete(idemKey);
                throw new BusinessException(FlashSaleErrorCode.OUT_OF_STOCK);
            }

            dbDeducted = true;

            try {
                wsBroadcaster.broadcastStockUpdate(slot.getId(), item.getId(), item.getAvailableStock() - request.getQuantity());
            } catch (Exception e) {
                log.warn("[WS] Failed to broadcast stock update for itemId={}: {}", item.getId(), e.getMessage());
            }

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

            OrderRef orderRef = orderPort.createPendingOrder(cmd);

            // Giao dịch ghi DB thành công -> Xóa bản ghi giữ chỗ tạm trên Redis
            if (redisTemplate != null) {
                redisTemplate.delete(PENDING_RES_PREFIX + orderCode);
                redisTemplate.opsForZSet().remove(PENDING_RES_INDEX, orderCode);
                if (idemKey != null) {
                    redisTemplate.opsForValue().set(idemKey, orderRef.orderCode(), Duration.ofMinutes(10));
                }
            }

            // Gửi ORDER_RESERVED riêng tới buyer qua WebSocket
            try {
                User buyer = (userRepository != null)
                        ? userRepository.findById(userId).orElse(null) : null;
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
                log.warn("[WS] Failed to send ORDER_RESERVED to user {}: {}", userId, wsEx.getMessage());
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

        } catch (BusinessException be) {
            throw be;
        } catch (Exception ex) {
            log.error("Error creating order down the line. Executing precise compensation. redisDeducted={}, dbDeducted={}",
                    redisDeducted, dbDeducted, ex);
            try {
                if (redisDeducted) {
                    stockReservationStrategy.rollbackStockWithMarker(slot.getId(), userId, item.getId(), request.getQuantity(), orderCode);
                }
                if (dbDeducted) {
                    itemRepository.replenishAvailableStockConditionally(item.getId(), request.getQuantity());
                }
                try {
                    itemRepository.findById(item.getId()).ifPresent(i -> {
                        int restoredStock = i.getAvailableStock();
                        wsBroadcaster.broadcastStockRestored(slot.getId(), i.getId(), restoredStock, request.getQuantity());
                    });
                } catch (Exception wsEx) {
                    log.warn("[WS] Failed to broadcast stock restoration for itemId={}: {}", item.getId(), wsEx.getMessage());
                }
                if (idemKey != null && redisTemplate != null) {
                    redisTemplate.delete(idemKey);
                }
            } catch (Exception compEx) {
                log.error("CRITICAL: Failed during precise compensation for item ID: {}", item.getId(), compEx);
            }
            throw new BusinessException(FlashSaleErrorCode.ORDER_CREATION_FAILED);
        }
    }

    /**
     * Hủy giữ chỗ Flash Sale (Mục 9.3).
     * Dùng chung logic hoàn kho với hủy đơn của Buyer.
     */
    @Transactional
    public CancelReservationResponse cancelReservation(Long userId, String reservationId) {
        if (orderRepository == null) {
            throw new BusinessException(FlashSaleErrorCode.RESERVATION_NOT_FOUND);
        }

        Order order = orderRepository.findByOrderCode(reservationId)
                .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.RESERVATION_NOT_FOUND));

        if (!order.getBuyer().getId().equals(userId)) {
            throw new BusinessException(FlashSaleErrorCode.NOT_STORE_OWNER);
        }

        // Idempotency: nếu đã hủy trước đó thì trả về thành công an toàn
        if ("CANCELLED_USER".equalsIgnoreCase(order.getStatus()) || "CANCELLED_TIMEOUT".equalsIgnoreCase(order.getStatus())) {
            return CancelReservationResponse.builder()
                    .success(true)
                    .reservationId(reservationId)
                    .message("Giữ chỗ đã được hủy trước đó.")
                    .build();
        }

        // Đợt này chỉ cho phép hủy đơn chưa thanh toán (PENDING_PAYMENT)
        if (!"PENDING_PAYMENT".equalsIgnoreCase(order.getStatus()) && !"CREATED".equalsIgnoreCase(order.getStatus())) {
            throw new BusinessException(FlashSaleErrorCode.CANNOT_CANCEL_NON_PENDING_RESERVATION);
        }

        // Chuyển trạng thái đơn sang CANCELLED_USER
        order.setStatus("CANCELLED_USER");
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        // Hoàn kho cho các mục Flash Sale trong đơn
        if (orderItemRepository != null) {
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
            for (OrderItem item : items) {
                if (item.getFlashSaleItem() != null) {
                    FlashSaleItem fsItem = item.getFlashSaleItem();
                    itemRepository.replenishAvailableStockConditionally(fsItem.getId(), item.getQuantity());

                    Long slotId = order.getSlot() != null ? order.getSlot().getId() : fsItem.getSlot().getId();
                    stockReservationStrategy.rollbackStockWithMarker(
                            slotId,
                            userId,
                            fsItem.getId(),
                            item.getQuantity(),
                            order.getOrderCode()
                    );

                    try {
                        itemRepository.findById(fsItem.getId()).ifPresent(refreshed -> {
                            wsBroadcaster.broadcastStockRestored(slotId, refreshed.getId(), refreshed.getAvailableStock(), item.getQuantity());
                        });
                    } catch (Exception e) {
                        log.warn("[WS] Failed to broadcast stock restored for itemId={}: {}", fsItem.getId(), e.getMessage());
                    }
                }
            }
        }

        if (order.getVoucher() != null && voucherService != null) {
            voucherService.rollbackVoucherUsage(order.getId());
        }

        log.info("Khách hàng userId={} đã hủy thành công giữ chỗ reservationId={}", userId, reservationId);
        return CancelReservationResponse.builder()
                .success(true)
                .reservationId(reservationId)
                .message("Hủy giữ chỗ thành công và hoàn trả tồn kho.")
                .build();
    }

    /**
     * Job đối soát tự động xử lý rò rỉ tồn kho khi tiến trình sập giữa bước trừ Redis và ghi DB.
     * Quét ZSET flash_sale:pending_res_index với score <= (now - 15s grace period).
     */
    @Scheduled(fixedDelay = 30000)
    public void reconcilePendingReservations() {
        if (redisTemplate == null || orderRepository == null) {
            return;
        }

        try {
            long nowEpoch = Instant.now().getEpochSecond();
            long thresholdEpoch = nowEpoch - 15; // 15 giây grace period (DB timeout là 3s, nên 15s chắc chắn DB đã xong hoặc rollback)

            Set<String> pendingOrderCodes = redisTemplate.opsForZSet().rangeByScore(PENDING_RES_INDEX, 0, thresholdEpoch);
            if (pendingOrderCodes == null || pendingOrderCodes.isEmpty()) {
                return;
            }

            log.info("[Reconciliation] Tìm thấy {} bản ghi giữ chỗ tạm quá hạn ân hạn cần đối soát", pendingOrderCodes.size());

            for (String orderCode : pendingOrderCodes) {
                try {
                    // Kiểm tra xem đơn đã tồn tại trong DB chưa
                    boolean orderExists = orderRepository.findByOrderCode(orderCode).isPresent();

                    if (orderExists) {
                        // Đơn đã ghi DB thành công -> chỉ cần dọn dẹp bản ghi tạm trên Redis
                        redisTemplate.delete(PENDING_RES_PREFIX + orderCode);
                        redisTemplate.opsForZSet().remove(PENDING_RES_INDEX, orderCode);
                        log.debug("[Reconciliation] Đơn hàng {} đã tồn tại trong DB. Đã dọn dẹp bản ghi tạm.", orderCode);
                    } else {
                        // Đơn KHÔNG tồn tại trong DB -> Tiến trình đã bị sập trước khi ghi DB!
                        String payloadStr = redisTemplate.opsForValue().get(PENDING_RES_PREFIX + orderCode);
                        if (payloadStr != null && !payloadStr.isBlank()) {
                            JsonNode json = objectMapper.readTree(payloadStr);
                            Long slotId = json.get("slotId").asLong();
                            Long userId = json.get("userId").asLong();
                            Long itemId = json.get("itemId").asLong();
                            int quantity = json.get("quantity").asInt();

                            log.warn("[Reconciliation] Phát hiện rò rỉ kho do crash cho orderCode={}, itemId={}, userId={}, qty={}. Đang hoàn tồn kho...",
                                    orderCode, itemId, userId, quantity);

                            // Thực thi hoàn tồn kho nguyên tử bằng Lua script
                            stockReservationStrategy.rollbackStockWithMarker(slotId, userId, itemId, quantity, orderCode);
                        } else {
                            // Không có payload, dọn dẹp index
                            redisTemplate.opsForZSet().remove(PENDING_RES_INDEX, orderCode);
                        }
                    }
                } catch (Exception e) {
                    log.error("[Reconciliation] Lỗi khi đối soát bản ghi orderCode: {}", orderCode, e);
                }
            }
        } catch (Exception e) {
            log.error("[Reconciliation] Lỗi trong reconcilePendingReservations: {}", e.getMessage(), e);
        }
    }
}
