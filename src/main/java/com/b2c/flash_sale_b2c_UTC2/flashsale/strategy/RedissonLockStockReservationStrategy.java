package com.b2c.flash_sale_b2c_UTC2.flashsale.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * RedissonLockStockReservationStrategy đóng vai trò trừ tồn kho và kiểm tra purchase limit
 * trên Redis khi đã nằm trong Distributed Lock (được FlashSaleReservationFacade bao bọc bên ngoài transaction).
 * Bản thân phương thức không tự xin lock nữa nhằm đảm bảo Lock bao trùm toàn bộ Database Transaction.
 */
@Slf4j
@Component("redissonLockStockReservationStrategy")
@ConditionalOnProperty(name = "flashsale.reservation.strategy", havingValue = "redisson")
public class RedissonLockStockReservationStrategy implements StockReservationStrategy {

    private final StringRedisTemplate redisTemplate;

    public static final String STOCK_KEY_PREFIX = "flash_sale:stock:";
    public static final String USER_LIMIT_KEY_PREFIX = "flash_sale:user_limit:";

    @Autowired
    public RedissonLockStockReservationStrategy(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long reserveStock(Long slotId, Long userId, Long itemId, int quantity, int userPurchaseLimit, long slotRemainingTtlSeconds) {
        return reserveStock(slotId, userId, itemId, quantity, userPurchaseLimit, slotRemainingTtlSeconds, null);
    }

    @Override
    public long reserveStock(Long slotId, Long userId, Long itemId, int quantity, int userPurchaseLimit, long slotRemainingTtlSeconds, String orderCode) {
        if (redisTemplate == null) {
            log.warn("Redis not available. Reservation skipped.");
            return 1;
        }

        String stockKey = STOCK_KEY_PREFIX + itemId;
        String userLimitKey = USER_LIMIT_KEY_PREFIX + slotId + ":" + userId + ":" + itemId;

        // 1. Kiểm tra purchase limit của user trên Redis
        String userCountStr = redisTemplate.opsForValue().get(userLimitKey);
        int userPurchased = userCountStr != null ? Integer.parseInt(userCountStr) : 0;
        if (userPurchased + quantity > userPurchaseLimit) {
            return -1;
        }

        // 2. Kiểm tra tồn kho khả dụng trên Redis
        String stockStr = redisTemplate.opsForValue().get(stockKey);
        int currentStock = stockStr != null ? Integer.parseInt(stockStr) : 0;
        if (currentStock < quantity) {
            return 0;
        }

        // 3. Trừ tồn kho và tăng user purchase limit
        redisTemplate.opsForValue().decrement(stockKey, quantity);
        redisTemplate.opsForValue().increment(userLimitKey, quantity);
        if (slotRemainingTtlSeconds > 0) {
            redisTemplate.expire(userLimitKey, Duration.ofSeconds(slotRemainingTtlSeconds));
        }

        return 1;
    }

    @Override
    public void compensate(Long slotId, Long userId, Long itemId, int quantity) {
        if (redisTemplate == null) {
            return;
        }

        String stockKey = STOCK_KEY_PREFIX + itemId;
        String userLimitKey = USER_LIMIT_KEY_PREFIX + slotId + ":" + userId + ":" + itemId;

        try {
            redisTemplate.opsForValue().increment(stockKey, quantity);
            Long remainingLimit = redisTemplate.opsForValue().decrement(userLimitKey, quantity);
            if (remainingLimit != null && remainingLimit <= 0) {
                redisTemplate.delete(userLimitKey);
            }
            log.info("Compensated Redisson reservation stock (+{}) and limit for FlashSaleItem ID: {}, user ID: {}", quantity, itemId, userId);
        } catch (Exception e) {
            log.error("Failed to compensate in Redisson strategy for item ID: {}, user ID: {}: {}", itemId, userId, e.getMessage(), e);
        }
    }

    @Override
    public boolean rollbackStockWithMarker(Long slotId, Long userId, Long itemId, int quantity, String orderCode) {
        compensate(slotId, userId, itemId, quantity);
        return true;
    }

    @Override
    public void rollbackStockSafe(Long slotId, Long userId, Long itemId, int quantity, int fallbackStock, long remainingTtlSeconds) {
        if (redisTemplate == null) {
            return;
        }

        String stockKey = STOCK_KEY_PREFIX + itemId;
        String userLimitKey = USER_LIMIT_KEY_PREFIX + slotId + ":" + userId + ":" + itemId;

        Boolean exists = redisTemplate.hasKey(stockKey);
        if (Boolean.TRUE.equals(exists)) {
            redisTemplate.opsForValue().increment(stockKey, quantity);
        } else {
            if (fallbackStock >= 0) {
                if (remainingTtlSeconds > 0) {
                    redisTemplate.opsForValue().set(stockKey, String.valueOf(fallbackStock), Duration.ofSeconds(remainingTtlSeconds));
                } else {
                    redisTemplate.opsForValue().set(stockKey, String.valueOf(fallbackStock));
                }
            }
        }

        Long remainingLimit = redisTemplate.opsForValue().decrement(userLimitKey, quantity);
        if (remainingLimit != null && remainingLimit <= 0) {
            redisTemplate.delete(userLimitKey);
        }
    }
}
