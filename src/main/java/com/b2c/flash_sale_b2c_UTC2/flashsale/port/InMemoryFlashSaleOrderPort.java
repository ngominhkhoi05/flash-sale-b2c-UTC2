package com.b2c.flash_sale_b2c_UTC2.flashsale.port;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation tạm thời trong bộ nhớ của FlashSaleOrderPort phục vụ test và chạy độc lập
 * khi module Order thật chưa được tích hợp.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(type = "com.b2c.flash_sale_b2c_UTC2.order.port.RealFlashSaleOrderPort")
public class InMemoryFlashSaleOrderPort implements FlashSaleOrderPort {

    private final AtomicLong idGenerator = new AtomicLong(1000L);
    private final Map<Long, StoredOrder> orders = new ConcurrentHashMap<>();

    private record StoredOrder(
            Long orderId,
            String orderCode,
            Long flashSaleItemId,
            Long slotId,
            Long userId,
            Integer quantity,
            String status,
            Instant expiresAt
    ) {
    }

    @Override
    public OrderRef createPendingOrder(CreateFlashSaleOrderCommand cmd) {
        Long orderId = idGenerator.incrementAndGet();
        StoredOrder order = new StoredOrder(
                orderId,
                cmd.orderCode(),
                cmd.flashSaleItemId(),
                cmd.slotId(),
                cmd.userId(),
                cmd.quantity(),
                "PENDING_PAYMENT",
                cmd.expiresAt()
        );
        orders.put(orderId, order);
        log.info("[InMemoryFlashSaleOrderPort] Tạo đơn PENDING_PAYMENT thành công: orderId={}, orderCode={}", orderId, cmd.orderCode());
        return new OrderRef(orderId, cmd.orderCode());
    }

    @Override
    public List<ExpiredOrderRef> lockExpiredPendingOrders(Instant now, int batchSize) {
        List<ExpiredOrderRef> expiredList = new ArrayList<>();
        for (StoredOrder order : orders.values()) {
            if ("PENDING_PAYMENT".equalsIgnoreCase(order.status()) && order.expiresAt().isBefore(now)) {
                expiredList.add(new ExpiredOrderRef(
                        order.orderId(),
                        order.orderCode(),
                        order.flashSaleItemId(),
                        order.slotId(),
                        order.userId(),
                        order.quantity()
                ));
                if (expiredList.size() >= batchSize) {
                    break;
                }
            }
        }
        return expiredList;
    }

    @Override
    public boolean cancelTimeoutIfPending(Long orderId) {
        StoredOrder existing = orders.get(orderId);
        if (existing != null && "PENDING_PAYMENT".equalsIgnoreCase(existing.status())) {
            StoredOrder cancelled = new StoredOrder(
                    existing.orderId(),
                    existing.orderCode(),
                    existing.flashSaleItemId(),
                    existing.slotId(),
                    existing.userId(),
                    existing.quantity(),
                    "CANCELLED_TIMEOUT",
                    existing.expiresAt()
            );
            orders.put(orderId, cancelled);
            log.info("[InMemoryFlashSaleOrderPort] Đã chuyển orderId={} sang CANCELLED_TIMEOUT", orderId);
            return true;
        }
        return false;
    }

    @Override
    public long countPendingBySlot(Long slotId) {
        return orders.values().stream()
                .filter(o -> "PENDING_PAYMENT".equalsIgnoreCase(o.status()) && (slotId == null || slotId.equals(o.slotId())))
                .count();
    }

    public void clear() {
        orders.clear();
    }
}
