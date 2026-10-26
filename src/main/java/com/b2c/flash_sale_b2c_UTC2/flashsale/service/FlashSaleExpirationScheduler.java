package com.b2c.flash_sale_b2c_UTC2.flashsale.service;

import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.ExpiredOrderRef;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.FlashSaleOrderPort;
import com.b2c.flash_sale_b2c_UTC2.flashsale.realtime.FlashSaleWsBroadcaster;
import com.b2c.flash_sale_b2c_UTC2.flashsale.realtime.FlashSaleWsEvent;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleSlotRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.strategy.StockReservationStrategy;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleExpirationScheduler {

    public static final String STOCK_KEY_PREFIX = "flash_sale:stock:";

    private final FlashSaleSlotRepository slotRepository;
    private final FlashSaleItemRepository itemRepository;
    private final StockReservationStrategy stockReservationStrategy;
    private final FlashSaleWsBroadcaster wsBroadcaster;
    private final FlashSaleSlotCloseService slotCloseService;

    @Autowired(required = false)
    private FlashSaleOrderPort orderPort;

    @Autowired(required = false)
    private UserRepository userRepository;

    @Autowired(required = false)
    private FlashSaleItemService itemService;

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
                    boolean cancelled = orderPort.cancelTimeoutIfPending(ref.orderId());
                    if (cancelled) {
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

        itemRepository.replenishAvailableStockConditionally(ref.flashSaleItemId(), ref.quantity());

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

        try {
            if (userRepository != null) {
                User buyer = userRepository.findById(ref.userId()).orElse(null);
                if (buyer != null) {
                    FlashSaleWsEvent event = FlashSaleWsEvent.builder()
                            .eventType(FlashSaleWsEvent.EventType.ORDER_CANCELLED_TIMEOUT)
                            .orderId(ref.orderId())
                            .orderCode(ref.orderCode())
                            .occurredAt(Instant.now())
                            .build();
                    wsBroadcaster.sendOrderCancelledToUser(buyer.getEmail(), event);
                }
            }
        } catch (Exception e) {
            log.warn("[WS] Failed to send ORDER_CANCELLED_TIMEOUT to user {}: {}",
                    ref.userId(), e.getMessage());
        }

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
     * Job 2: Quét các khung giờ Flash Sale để tự động kích hoạt phiên khi đến giờ và đóng phiên khi hết giờ.
     * Chạy định kỳ mỗi 5 giây.
     */
    @Scheduled(fixedDelay = 5000)
    public void processEndedSlotsAndReturnUnsoldStock() {
        try {
            Instant now = Instant.now();
            List<FlashSaleSlot> endedSlots = slotRepository.findSlotsToEnd(now);

            for (FlashSaleSlot slot : endedSlots) {
                try {
                    slotCloseService.closeAndReturnStock(slot);
                } catch (Exception e) {
                    log.error("Failed to close slot ID: {}", slot.getId(), e);
                }
            }

            List<FlashSaleSlot> slotsToActivate = slotRepository.findSlotsToActivate(now);
            for (FlashSaleSlot slot : slotsToActivate) {
                slot.setStatus("ACTIVE");
                slotRepository.save(slot);
                log.info("Activated FlashSaleSlot ID: {} - {}", slot.getId(), slot.getTitle());

                // Tự động nạp tồn kho lên Redis
                if (itemService != null) {
                    try {
                        itemService.preWarmRedisForSlot(slot.getId());
                    } catch (Exception e) {
                        log.warn("Auto pre-warm for slot {} failed: {}", slot.getId(), e.getMessage());
                    }
                }

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
}

