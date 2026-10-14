package com.b2c.flash_sale_b2c_UTC2.flashsale.service;

import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.ExpiredOrderRef;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.FlashSaleOrderPort;
import com.b2c.flash_sale_b2c_UTC2.flashsale.realtime.FlashSaleWsBroadcaster;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleSlotRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.strategy.StockReservationStrategy;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleExpirationScheduler {

    private final FlashSaleSlotRepository slotRepository;
    private final FlashSaleItemRepository itemRepository;
    private final ProductVariantRepository variantRepository;
    private final StockReservationStrategy stockReservationStrategy;
    private final FlashSaleWsBroadcaster wsBroadcaster;

    @Autowired(required = false)
    private FlashSaleOrderPort orderPort;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    public static final String STOCK_KEY_PREFIX = "flash_sale:stock:";

    /**
     * Job 1: Quét các đơn Flash Sale PENDING_PAYMENT hết hạn giữ chỗ (quá 300s).
     * Chạy định kỳ mỗi 15 giây.
     */
    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void processExpiredReservations() {
        if (orderPort == null) {
            return;
        }

        try {
            Instant now = Instant.now();
            List<ExpiredOrderRef> expiredOrders = orderPort.lockExpiredPendingOrders(now, 100);
            if (expiredOrders.isEmpty()) {
                return;
            }

            log.info("Found {} expired flash sale orders to process", expiredOrders.size());

            for (ExpiredOrderRef ref : expiredOrders) {
                try {
                    // Update PENDING_PAYMENT -> CANCELLED_TIMEOUT conditionally
                    boolean cancelled = orderPort.cancelTimeoutIfPending(ref.orderId());
                    if (cancelled) {
                        // Idempotent: Only refund if this call actually transitioned the order state
                        rollbackExpiredOrderStock(ref);
                    } else {
                        log.debug("Order ID: {} was already handled or paid. Skipping rollback.", ref.orderId());
                    }
                } catch (Exception e) {
                    log.error("Failed to process timeout rollback for order ID: {}", ref.orderId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error in processExpiredReservations scheduled task: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void rollbackExpiredOrderStock(ExpiredOrderRef ref) {
        log.info("Rolling back stock for expired order ID: {}, item ID: {}, qty: {}",
                ref.orderId(), ref.flashSaleItemId(), ref.quantity());

        // 1. Rollback DB available_stock conditionally (available_stock + qty <= allocated_stock)
        itemRepository.replenishAvailableStockConditionally(ref.flashSaleItemId(), ref.quantity());

        // Broadcast stock restored via WebSocket
        try {
            FlashSaleItem refreshed = itemRepository.findById(ref.flashSaleItemId()).orElse(null);
            if (refreshed != null) {
                wsBroadcaster.broadcastStockRestored(ref.slotId(), ref.flashSaleItemId(),
                        refreshed.getAvailableStock(), ref.quantity());
            }
        } catch (Exception e) {
            log.warn("[WS] Failed to broadcast stock restoration for expired order itemId={}: {}",
                    ref.flashSaleItemId(), e.getMessage());
        }

        // 2. Safe rollback of Redis stock and purchase limit (verifying key existence or reconstructing from DB)
        FlashSaleItem item = itemRepository.findById(ref.flashSaleItemId()).orElse(null);
        if (item != null) {
            long remainingPending = 0;
            if (orderPort != null && ref.slotId() != null) {
                remainingPending = orderPort.countPendingBySlot(ref.slotId());
            }
            int fallbackStock = item.getAvailableStock() + (int) remainingPending;
            long remainingTtlSeconds = item.getSlot() != null 
                    ? java.time.Duration.between(Instant.now(), item.getSlot().getEndTime()).getSeconds() 
                    : 300;

            stockReservationStrategy.rollbackStockSafe(
                    ref.slotId(),
                    ref.userId(),
                    ref.flashSaleItemId(),
                    ref.quantity(),
                    fallbackStock,
                    remainingTtlSeconds
            );
        } else {
            stockReservationStrategy.compensate(ref.slotId(), ref.userId(), ref.flashSaleItemId(), ref.quantity());
        }
    }

    /**
     * Job 2: Quét các khung giờ Flash Sale đã kết thúc để tự động hoàn trả tồn kho chưa bán (unsold stock).
     * Chạy định kỳ mỗi 30 giây.
     */
    @Scheduled(fixedDelay = 30000)
    public void processEndedSlotsAndReturnUnsoldStock() {
        try {
            Instant now = Instant.now();
            List<FlashSaleSlot> endedSlots = slotRepository.findSlotsToEnd(now);

            for (FlashSaleSlot slot : endedSlots) {
                try {
                    closeSlotAndReturnStock(slot);
                } catch (Exception e) {
                    log.error("Failed to close slot ID: {}", slot.getId(), e);
                }
            }

            // Đồng thời kích hoạt các slot UPCOMING đã tới giờ
            List<FlashSaleSlot> slotsToActivate = slotRepository.findSlotsToActivate(now);
            for (FlashSaleSlot slot : slotsToActivate) {
                slot.setStatus("ACTIVE");
                slotRepository.save(slot);
                log.info("Activated FlashSaleSlot ID: {} - {}", slot.getId(), slot.getTitle());
                // Broadcast slot activated via WebSocket
                try {
                    wsBroadcaster.broadcastSlotStatus(slot.getId(), "ACTIVE");
                } catch (Exception e) {
                    log.warn("[WS] Failed to broadcast slot ACTIVE for slotId={}: {}",
                            slot.getId(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Error in processEndedSlotsAndReturnUnsoldStock scheduled task: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void closeSlotAndReturnStock(FlashSaleSlot slot) {
        // Kiểm tra xem còn đơn PENDING_PAYMENT nào chưa xử lý của slot này không
        if (orderPort != null) {
            long pendingOrders = orderPort.countPendingBySlot(slot.getId());
            if (pendingOrders > 0) {
                log.info("Slot ID: {} still has {} pending reservations. Waiting for timeout or payment before closing.",
                        slot.getId(), pendingOrders);
                return;
            }
        }

        log.info("Closing FlashSaleSlot ID: {} and returning unsold stock...", slot.getId());
        List<FlashSaleItem> items = itemRepository.findBySlotId(slot.getId());

        for (FlashSaleItem item : items) {
            if ("APPROVED".equals(item.getStatus())) {
                int unsoldStock = item.getAvailableStock();
                if (unsoldStock > 0) {
                    // Trả lại kho gốc của biến thể
                    variantRepository.replenishStockQuantityConditionally(
                            item.getVariant().getId(),
                            unsoldStock
                    );
                    log.info("Returned {} unsold stock to variant ID: {} from FlashSaleItem ID: {}",
                            unsoldStock, item.getVariant().getId(), item.getId());
                }

                // Broadcast unsold stock returned via WebSocket
                try {
                    wsBroadcaster.broadcastUnsoldStockReturned(slot.getId(), item.getId(), 0);
                } catch (Exception e) {
                    log.warn("[WS] Failed to broadcast unsold stock returned for itemId={}: {}",
                            item.getId(), e.getMessage());
                }

                item.setStatus("ENDED");
                itemRepository.save(item);

                // Dọn dẹp key Redis
                if (redisTemplate != null) {
                    try {
                        redisTemplate.delete(STOCK_KEY_PREFIX + item.getId());
                    } catch (Exception e) {
                        log.warn("Failed to delete Redis stock key for item ID: {}", item.getId());
                    }
                }
            }
        }

        slot.setStatus("ENDED");
        slotRepository.save(slot);
        log.info("FlashSaleSlot ID: {} successfully closed with status ENDED.", slot.getId());

        // Broadcast slot closed via WebSocket
        try {
            wsBroadcaster.broadcastSlotStatus(slot.getId(), "ENDED");
        } catch (Exception e) {
            log.warn("[WS] Failed to broadcast slot ENDED for slotId={}: {}",
                    slot.getId(), e.getMessage());
        }
    }
}
