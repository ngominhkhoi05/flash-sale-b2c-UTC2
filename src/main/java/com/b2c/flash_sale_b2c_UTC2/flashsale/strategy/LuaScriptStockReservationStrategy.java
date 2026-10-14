package com.b2c.flash_sale_b2c_UTC2.flashsale.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component("luaScriptStockReservationStrategy")
@ConditionalOnProperty(name = "flashsale.reservation.strategy", havingValue = "lua", matchIfMissing = true)
public class LuaScriptStockReservationStrategy implements StockReservationStrategy {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> reserveScript;
    private final DefaultRedisScript<Long> rollbackScript;

    public static final String STOCK_KEY_PREFIX = "flash_sale:stock:";
    public static final String USER_LIMIT_KEY_PREFIX = "flash_sale:user_limit:";

    @Autowired
    public LuaScriptStockReservationStrategy(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.reserveScript = new DefaultRedisScript<>();
        this.reserveScript.setLocation(new ClassPathResource("lua/reserve_stock.lua"));
        this.reserveScript.setResultType(Long.class);

        this.rollbackScript = new DefaultRedisScript<>();
        this.rollbackScript.setLocation(new ClassPathResource("lua/rollback_stock.lua"));
        this.rollbackScript.setResultType(Long.class);
    }

    @Override
    public long reserveStock(Long slotId, Long userId, Long itemId, int quantity, int userPurchaseLimit, long slotRemainingTtlSeconds) {
        return reserveStock(slotId, userId, itemId, quantity, userPurchaseLimit, slotRemainingTtlSeconds, null);
    }

    @Override
    public long reserveStock(Long slotId, Long userId, Long itemId, int quantity, int userPurchaseLimit, long slotRemainingTtlSeconds, String orderCode) {
        if (redisTemplate == null) {
            log.warn("Redis is not available. Lua reservation skipped.");
            return 1;
        }

        String stockKey = STOCK_KEY_PREFIX + itemId;
        String userLimitKey = USER_LIMIT_KEY_PREFIX + slotId + ":" + userId + ":" + itemId;
        String pendingResKey = (orderCode != null && !orderCode.isBlank())
                ? "flash_sale:pending_res:" + orderCode : "flash_sale:dummy_pending";
        String pendingIndexKey = "flash_sale:pending_res_index";

        try {
            Long result = redisTemplate.execute(
                    reserveScript,
                    List.of(stockKey, userLimitKey, pendingResKey, pendingIndexKey),
                    String.valueOf(quantity),
                    String.valueOf(userPurchaseLimit),
                    String.valueOf(Math.max(slotRemainingTtlSeconds, 60)),
                    orderCode != null ? orderCode : "",
                    String.valueOf(userId),
                    String.valueOf(slotId),
                    String.valueOf(itemId),
                    String.valueOf(86400) // 24 hours TTL for pending record
            );

            if (result == null || result == -2) {
                return 0; // out of stock
            }
            if (result == -1) {
                return -1; // user limit exceeded
            }
            return 1; // success (result >= 0)
        } catch (Exception e) {
            log.error("Error executing reserve_stock.lua for item ID: {}, user ID: {}: {}", itemId, userId, e.getMessage());
            throw e;
        }
    }

    @Override
    public boolean rollbackStockWithMarker(Long slotId, Long userId, Long itemId, int quantity, String orderCode) {
        if (redisTemplate == null) {
            return true;
        }

        String stockKey = STOCK_KEY_PREFIX + itemId;
        String userLimitKey = USER_LIMIT_KEY_PREFIX + slotId + ":" + userId + ":" + itemId;
        String markerKey = "flash_sale:rollback:" + orderCode + ":" + itemId;
        String pendingResKey = "flash_sale:pending_res:" + orderCode;
        String pendingIndexKey = "flash_sale:pending_res_index";

        try {
            Long result = redisTemplate.execute(
                    rollbackScript,
                    List.of(stockKey, userLimitKey, markerKey, pendingResKey, pendingIndexKey),
                    String.valueOf(quantity),
                    String.valueOf(86400), // marker TTL 24h
                    orderCode != null ? orderCode : ""
            );
            log.info("Atomic rollbackStockWithMarker executed for orderCode: {}, itemId: {}. Result: {}",
                    orderCode, itemId, result);
            return result != null && result > 0;
        } catch (Exception e) {
            log.error("Failed to execute atomic rollback for orderCode: {}, itemId: {}: {}", orderCode, itemId, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void compensate(Long slotId, Long userId, Long itemId, int quantity) {
        if (redisTemplate == null) {
            return;
        }

        String stockKey = STOCK_KEY_PREFIX + itemId;
        String userLimitKey = USER_LIMIT_KEY_PREFIX + slotId + ":" + userId + ":" + itemId;
        String dummyMarkerKey = "flash_sale:rollback:comp_" + System.currentTimeMillis() + ":" + itemId;
        String dummyPendingResKey = "flash_sale:dummy_pending";
        String dummyIndexKey = "flash_sale:dummy_index";

        try {
            redisTemplate.execute(
                    rollbackScript,
                    List.of(stockKey, userLimitKey, dummyMarkerKey, dummyPendingResKey, dummyIndexKey),
                    String.valueOf(quantity),
                    "60",
                    ""
            );
            log.info("Compensated Redis stock (+{}) and limit for FlashSaleItem ID: {}, user ID: {}", quantity, itemId, userId);
        } catch (Exception e) {
            log.error("Failed to compensate Redis for item ID: {}, user ID: {}: {}", itemId, userId, e.getMessage(), e);
        }
    }

    @Override
    public void rollbackStockSafe(Long slotId, Long userId, Long itemId, int quantity, int fallbackStock, long remainingTtlSeconds) {
        if (redisTemplate == null) {
            return;
        }

        String stockKey = STOCK_KEY_PREFIX + itemId;
        String userLimitKey = USER_LIMIT_KEY_PREFIX + slotId + ":" + userId + ":" + itemId;
        String markerKey = "flash_sale:rollback:safe_" + System.currentTimeMillis() + ":" + itemId;
        String dummyPendingResKey = "flash_sale:dummy_pending";
        String dummyIndexKey = "flash_sale:dummy_index";

        try {
            Long result = redisTemplate.execute(
                    rollbackScript,
                    List.of(stockKey, userLimitKey, markerKey, dummyPendingResKey, dummyIndexKey),
                    String.valueOf(quantity),
                    String.valueOf(Math.max(remainingTtlSeconds, 60)),
                    ""
            );
            log.info("Safe rollback of stock executed for item ID: {}. Redis stock is now: {}", itemId, result);
        } catch (Exception e) {
            log.error("Failed to execute safe rollback for item ID: {}, user ID: {}: {}", itemId, userId, e.getMessage(), e);
        }
    }
}
