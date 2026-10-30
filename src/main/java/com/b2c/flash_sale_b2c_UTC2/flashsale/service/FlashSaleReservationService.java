package com.b2c.flash_sale_b2c_UTC2.flashsale.service;

import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.FlashSaleOrderStreamEvent;
import com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleOrderStreamConsumer;

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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    @Autowired(required = false)
    private GatedReservationTransactionalWorker gatedWorker;
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

        FlashSaleItem item = itemRepository.findByIdWithDetails(request.getFlashSaleItemId())
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

        if (item.getAvailableStock() == null) {
            item.setAvailableStock(item.getAllocatedStock() != null ? item.getAllocatedStock() : 0);
            item = itemRepository.save(item);
        }

        ensureStockOnRedis(item, slot, remainingSlotSeconds);

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
     * Luồng gated Lua (Việc 2):
     * 1. Validate & đọc dữ liệu không mở transaction (read-only / không giữ connection DB).
     * 2. Gọi Lua reserveStock trên Redis. Nếu thất bại -> trả kết quả ngay, KHÔNG chạm DB.
     * 3. Chỉ khi Lua thành công mới mở transaction DB ngắn: tạo order, UPDATE kho ở cuối cùng, và WS trong afterCommit.
     * 4. Bù hoàn chính xác trên Redis nếu ghi DB thất bại.
     */

    /**
     * Luồng Redisson Optimized (Ma trận 2x2):
     * Lock Redisson đã bao bọc bên ngoài tại Facade.
     * Tại đây:
     * 1. Validate & đọc dữ liệu không giữ connection DB.
     * 2. Trừ kho Redis in-memory (qua stockReservationStrategy.reserveStock).
     * 3. Chỉ khi trừ Redis thành công mới mở transaction DB ngắn qua GatedReservationTransactionalWorker
     *    (tạo order trước, UPDATE kho ở cuối, WS trong afterCommit).
     * 4. Bù trừ chính xác nếu ghi DB thất bại.
     */
    public ReservationResponse createReservationOptimizedRedisson(Long userId, String idempotencyKey, CreateReservationRequest request) {
        return createReservationGatedLua(userId, idempotencyKey, request);
    }


    /**
     * Big-Tech Async Queue Reservation (Cách 3 - Trừ kho Redis + Đẩy Queue Redis Stream):
     * 1. Kiểm tra Idempotency trên Redis in-memory (< 1ms).
     * 2. Validate nhanh: slot, user purchase limit, address ownership (read-only, không mở transaction).
     * 3. Trừ kho nguyên tử trên Redis bằng Lua script (< 2ms).
     * 4. Ghi bản ghi pending vào Redis và Publish event vào Redis Stream (`flash_sale:order_stream`).
     * 5. Trả về ngay lập tức HTTP 201/202 PENDING_PROCESSING cho Client trong < 10ms.
     * Worker phía sau (FlashSaleOrderStreamConsumer) sẽ tiêu thụ stream và lưu vào DB bất đồng bộ.
     */
    public ReservationResponse createReservationAsync(Long userId, String idempotencyKey, CreateReservationRequest request) {
        String idemKey = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank() && redisTemplate != null) {
            idemKey = IDEMPOTENCY_KEY_PREFIX + userId + ":" + idempotencyKey;
            Boolean isFirstReq = redisTemplate.opsForValue().setIfAbsent(idemKey, "PROCESSING", Duration.ofMinutes(10));
            if (Boolean.FALSE.equals(isFirstReq)) {
                String existingVal = redisTemplate.opsForValue().get(idemKey);
                if ("PROCESSING".equals(existingVal)) {
                    log.warn("[ASYNC] Duplicate reservation in flight for userId: {}, idempotencyKey: {}", userId, idempotencyKey);
                    throw new BusinessException(FlashSaleErrorCode.IDEMPOTENCY_CONFLICT);
                }
                if (existingVal != null && !existingVal.isBlank()) {
                    log.info("[ASYNC] Returning cached order result for idempotencyKey: {} -> {}", idempotencyKey, existingVal);
                    return ReservationResponse.builder()
                            .orderCode(existingVal)
                            .reservationId(existingVal)
                            .status("PENDING_PAYMENT")
                            .message("Yêu cầu đã được xử lý thành công trước đó (Idempotent replay).")
                            .build();
                }
            }
        }

        // 1. Validate & đọc dữ liệu không giữ connection DB
        Address address;
        if (request.getAddressId() != null) {
            address = addressRepository.findById(request.getAddressId())
                    .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.ADDRESS_NOT_FOUND_OR_NOT_OWNED));
            if (address.getUser() == null || !address.getUser().getId().equals(userId)) {
                throw new BusinessException(FlashSaleErrorCode.ADDRESS_NOT_FOUND_OR_NOT_OWNED);
            }
        } else {
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

        FlashSaleItem item = itemRepository.findByIdWithDetails(request.getFlashSaleItemId())
                .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.ITEM_NOT_FOUND));

        if (!"APPROVED".equals(item.getStatus())) {
            throw new BusinessException(FlashSaleErrorCode.ITEM_NOT_FOUND);
        }

        FlashSaleSlot slot = item.getSlot();
        Instant now = Instant.now();

        if (now.isBefore(slot.getStartTime()) || now.isAfter(slot.getEndTime()) || !"ACTIVE".equals(slot.getStatus())) {
            throw new BusinessException(FlashSaleErrorCode.SLOT_NOT_ACTIVE);
        }

        long remainingSlotSeconds = Duration.between(now, slot.getEndTime()).getSeconds();
        if (remainingSlotSeconds <= 0) {
            throw new BusinessException(FlashSaleErrorCode.SLOT_ALREADY_ENDED);
        }

        if (item.getAvailableStock() == null) {
            item.setAvailableStock(item.getAllocatedStock() != null ? item.getAllocatedStock() : 0);
            item = itemRepository.save(item);
        }

        ensureStockOnRedis(item, slot, remainingSlotSeconds);

        String orderCode = "FS-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 9000 + 1000);

        // 2. Gọi Lua reserve trên Redis
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

        // 3. Đẩy Order Event vào Redis Stream
        Instant expiresAt = Instant.now().plusSeconds(slot.getReservationTtlSeconds());
        BigDecimal totalAmount = item.getFlashSalePrice().multiply(BigDecimal.valueOf(request.getQuantity()));

        FlashSaleOrderStreamEvent event = FlashSaleOrderStreamEvent.builder()
                .userId(userId)
                .addressId(address.getId())
                .flashSaleItemId(item.getId())
                .slotId(slot.getId())
                .variantId(item.getVariant().getId())
                .storeId(item.getVariant().getProduct().getStore().getId())
                .quantity(request.getQuantity())
                .unitPrice(item.getFlashSalePrice())
                .productName(item.getVariant().getProduct().getName())
                .variantName(item.getVariant().getVariantName())
                .orderCode(orderCode)
                .expiresAt(expiresAt)
                .idempotencyKey(idempotencyKey)
                .createdAtEpochMs(System.currentTimeMillis())
                .build();

        try {
            if (redisTemplate != null) {
                String eventJson = objectMapper.writeValueAsString(event);
                java.util.Map<String, String> map = java.util.Map.of("payload", eventJson);
                redisTemplate.opsForStream().add(FlashSaleOrderStreamConsumer.STREAM_KEY, map);
                log.info("[ASYNC] Published order event to stream: orderCode={}", orderCode);
            }
        } catch (Exception ex) {
            log.error("[ASYNC] Failed to publish order event to Redis Stream for orderCode={}. Compensating Redis...", orderCode, ex);
            try {
                stockReservationStrategy.rollbackStockWithMarker(slot.getId(), userId, item.getId(), request.getQuantity(), orderCode);
                if (idemKey != null && redisTemplate != null) redisTemplate.delete(idemKey);
            } catch (Exception rollEx) {
                log.error("[ASYNC] Compensation failed for orderCode={}", orderCode, rollEx);
            }
            throw new BusinessException(FlashSaleErrorCode.ORDER_CREATION_FAILED);
        }

        // 4. Trả về ngay lập tức cho client
        return ReservationResponse.builder()
                .orderCode(orderCode)
                .reservationId(orderCode)
                .flashSaleItemId(item.getId())
                .quantity(request.getQuantity())
                .flashSalePrice(item.getFlashSalePrice())
                .totalAmount(totalAmount)
                .status("PROCESSING")
                .expiresAt(expiresAt)
                .message("Đã giữ chỗ Flash Sale thành công. Hệ thống đang tiến hành tạo đơn hàng.")
                .build();
    }

    public ReservationResponse createReservationGatedLua(Long userId, String idempotencyKey, CreateReservationRequest request) {
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

        // 1. Validate & đọc dữ liệu cần thiết (KHÔNG giữ connection)
        Address address;
        if (request.getAddressId() != null) {
            address = addressRepository.findById(request.getAddressId())
                    .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.ADDRESS_NOT_FOUND_OR_NOT_OWNED));
            if (address.getUser() == null || !address.getUser().getId().equals(userId)) {
                throw new BusinessException(FlashSaleErrorCode.ADDRESS_NOT_FOUND_OR_NOT_OWNED);
            }
        } else {
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

        FlashSaleItem item = itemRepository.findByIdWithDetails(request.getFlashSaleItemId())
                .orElseThrow(() -> new BusinessException(FlashSaleErrorCode.ITEM_NOT_FOUND));

        if (!"APPROVED".equals(item.getStatus())) {
            throw new BusinessException(FlashSaleErrorCode.ITEM_NOT_FOUND);
        }

        FlashSaleSlot slot = item.getSlot();
        Instant now = Instant.now();

        if (now.isBefore(slot.getStartTime()) || now.isAfter(slot.getEndTime()) || !"ACTIVE".equals(slot.getStatus())) {
            throw new BusinessException(FlashSaleErrorCode.SLOT_NOT_ACTIVE);
        }

        long remainingSlotSeconds = Duration.between(now, slot.getEndTime()).getSeconds();
        if (remainingSlotSeconds <= 0) {
            throw new BusinessException(FlashSaleErrorCode.SLOT_ALREADY_ENDED);
        }

        if (item.getAvailableStock() == null) {
            item.setAvailableStock(item.getAllocatedStock() != null ? item.getAllocatedStock() : 0);
            item = itemRepository.save(item);
        }

        ensureStockOnRedis(item, slot, remainingSlotSeconds);

        String orderCode = "FS-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 9000 + 1000);

        // 2. Gọi Lua reserve trên Redis
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

        // 3. Chỉ khi Lua thành công mới mở transaction DB ngắn
        try {
            return (gatedWorker != null) ? gatedWorker.executeGatedDatabaseReservation(userId, address, item, slot, request, orderCode, idemKey) : executeGatedDatabaseReservation(userId, address, item, slot, request, orderCode, idemKey);
        } catch (Exception ex) {
            log.error("DB reservation failed for orderCode={}. Compensating Redis Lua reservation...", orderCode, ex);
            try {
                stockReservationStrategy.rollbackStockWithMarker(slot.getId(), userId, item.getId(), request.getQuantity(), orderCode);
                if (idemKey != null && redisTemplate != null) {
                    redisTemplate.delete(idemKey);
                }
            } catch (Exception compEx) {
                log.error("CRITICAL: Failed during Redis compensation for orderCode: {}", orderCode, compEx);
            }
            if (ex instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(FlashSaleErrorCode.ORDER_CREATION_FAILED);
        }
    }

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

    public void ensureStockOnRedis(FlashSaleItem item, FlashSaleSlot slot, long remainingSlotSeconds) {
        if (redisTemplate == null) return;
        String stockKey = "flash_sale:stock:" + item.getId();
        try {
            String existingVal = redisTemplate.opsForValue().get(stockKey);
            if (existingVal == null) {
                int stockToSet = (item.getAvailableStock() != null && item.getAvailableStock() > 0)
                        ? item.getAvailableStock()
                        : (item.getAllocatedStock() != null ? item.getAllocatedStock() : 0);

                if (stockToSet > 0) {
                    redisTemplate.opsForValue().setIfAbsent(
                            stockKey,
                            String.valueOf(stockToSet),
                            Duration.ofSeconds(Math.max(remainingSlotSeconds, 60))
                    );
                    log.info("Auto pre-warmed missing Redis stock for FlashSaleItem ID: {} -> initialStock: {}", item.getId(), stockToSet);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to auto pre-warm Redis stock for item ID: {}: {}", item.getId(), e.getMessage());
        }
    }
}
