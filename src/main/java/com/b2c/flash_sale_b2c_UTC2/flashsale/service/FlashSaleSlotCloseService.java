package com.b2c.flash_sale_b2c_UTC2.flashsale.service;

import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.FlashSaleOrderPort;
import com.b2c.flash_sale_b2c_UTC2.flashsale.realtime.FlashSaleWsBroadcaster;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleSlotRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service that closes an ended Flash Sale slot and returns the unsold stock
 * back to the product variants.
 *
 * Extracted from FlashSaleExpirationScheduler so the @Transactional boundary
 * goes through the Spring AOP proxy. Previously the scheduler's outer method
 * was not transactional, and the inner close method was called via
 * {@code this.closeSlotAndReturnStock(...)} which bypasses the proxy and
 * triggered TransactionRequiredException on the @Modifying UPDATE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleSlotCloseService {

    public static final String STOCK_KEY_PREFIX = "flash_sale:stock:";

    private final FlashSaleSlotRepository slotRepository;
    private final FlashSaleItemRepository itemRepository;
    private final ProductVariantRepository variantRepository;
    private final FlashSaleWsBroadcaster wsBroadcaster;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private FlashSaleOrderPort orderPort;

    @Transactional
    public void closeAndReturnStock(FlashSaleSlot slot) {
        if (orderPort != null) {
            long pendingOrders = orderPort.countPendingBySlot(slot.getId());
            if (pendingOrders > 0) {
                log.info("Slot ID: {} still has {} pending reservations. Waiting for timeout or payment before closing.",
                        slot.getId(), pendingOrders);
                return;
            }
        }

        if (Boolean.TRUE.equals(slot.getIsStockRolledBack())) {
            log.info("Slot ID: {} already had stock rolled back. Skipping rollback to prevent double replenishment.", slot.getId());
            slot.setStatus("ENDED");
            slotRepository.save(slot);
            try {
                wsBroadcaster.broadcastSlotStatus(slot.getId(), "ENDED");
            } catch (Exception e) {
                log.warn("[WS] Failed to broadcast slot ENDED for slotId={}: {}", slot.getId(), e.getMessage());
            }
            return;
        }

        log.info("Closing FlashSaleSlot ID: {} and returning unsold stock...", slot.getId());
        List<FlashSaleItem> items = itemRepository.findBySlotId(slot.getId());

        for (FlashSaleItem item : items) {
            if ("APPROVED".equals(item.getStatus())) {
                int unsoldStock = item.getAvailableStock();
                // Khong hoan ton kho chua ban ve kho goc vi khi dang ky/duyet khong tru ton kho goc (chi tru khi ban duoc hang)
                log.info("Closing FlashSaleItem ID: {}, unsold quota: {} (base stock was not deducted on registration)", item.getId(), unsoldStock);
                item.setStatus("ENDED");
                itemRepository.save(item);

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

        try {
            wsBroadcaster.broadcastSlotStatus(slot.getId(), "ENDED");
        } catch (Exception e) {
            log.warn("[WS] Failed to broadcast slot ENDED for slotId={}: {}",
                    slot.getId(), e.getMessage());
        }
    }
}
