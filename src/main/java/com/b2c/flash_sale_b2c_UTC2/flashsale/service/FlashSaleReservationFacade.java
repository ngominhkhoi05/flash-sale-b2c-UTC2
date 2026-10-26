package com.b2c.flash_sale_b2c_UTC2.flashsale.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.CreateReservationRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.ReservationResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.exception.FlashSaleErrorCode;
import com.b2c.flash_sale_b2c_UTC2.flashsale.exception.LockAcquisitionException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Facade điều phối quy trình giữ chỗ Flash Sale.
 * - Trục A: cơ chế (Lua hoặc Redisson).
 * - Trục B: luồng transaction (legacy = transaction dài bao ngoài, hoặc optimized = worker giao dịch ngắn, UPDATE ở cuối, broadcast sau commit).
 */
@Slf4j
@Service
public class FlashSaleReservationFacade {

    private final FlashSaleReservationService reservationService;
    private final String strategy;
    private final String luaFlow;
    private final String txFlow;
    private final long waitMs;
    private final boolean failFastEnabled;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;

    public static final String LOCK_PREFIX = "lock:flash_sale:item:";
    public static final String STOCK_KEY_PREFIX = "flash_sale:stock:";

    @Autowired
    public FlashSaleReservationFacade(
            FlashSaleReservationService reservationService,
            @Value("${flashsale.reservation.strategy:lua}") String strategy,
            @Value("${flashsale.reservation.lua-flow:legacy}") String luaFlow,
            @Value("${flashsale.tx-flow:legacy}") String txFlow,
            @Value("${flashsale.lock.wait-ms:200}") long waitMs,
            @Value("${flashsale.fail-fast.enabled:false}") boolean failFastEnabled,
            @Autowired(required = false) RedissonClient redissonClient,
            @Autowired(required = false) StringRedisTemplate redisTemplate
    ) {
        this.reservationService = reservationService;
        this.strategy = strategy;
        this.luaFlow = luaFlow;
        this.txFlow = txFlow;
        this.waitMs = waitMs;
        this.failFastEnabled = failFastEnabled;
        this.redissonClient = redissonClient;
        this.redisTemplate = redisTemplate;
    }

    public ReservationResponse reserve(Long userId, String idempotencyKey, CreateReservationRequest request) {
        Long itemId = request.getFlashSaleItemId();
        String stockKey = STOCK_KEY_PREFIX + itemId;

        // Fail-fast check dùng chung (cho Redisson hoặc khi failFastEnabled=true với Lua)
        if ("redisson".equalsIgnoreCase(strategy) || failFastEnabled) {
            if (redisTemplate != null) {
                String stockStr = redisTemplate.opsForValue().get(stockKey);
                if (stockStr != null) {
                    int currentStock = Integer.parseInt(stockStr);
                    if (currentStock <= 0 || currentStock < request.getQuantity()) {
                        throw new BusinessException(FlashSaleErrorCode.OUT_OF_STOCK);
                    }
                }
            }
        }

        // --- NHÁNH LUA ---
        if (!"redisson".equalsIgnoreCase(strategy)) {
            if ("optimized".equalsIgnoreCase(txFlow) || "gated".equalsIgnoreCase(luaFlow)) {
                return reservationService.createReservationGatedLua(userId, idempotencyKey, request);
            }
            return reservationService.createReservation(userId, idempotencyKey, request);
        }

        // --- NHÁNH REDISSON DISTRIBUTED LOCK ---
        if (redissonClient == null) {
            log.warn("Redisson strategy selected but RedissonClient is null. Proceeding directly to service.");
            return reservationService.createReservation(userId, idempotencyKey, request);
        }

        String lockKey = LOCK_PREFIX + itemId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        long lockStart = System.nanoTime();
        try {
            acquired = lock.tryLock(waitMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("Could not acquire Redisson lock for itemId: {} within {}ms", itemId, waitMs);
                throw new LockAcquisitionException();
            }

            long acquiredNanos = System.nanoTime();
            try {
                if ("optimized".equalsIgnoreCase(txFlow)) {
                    return reservationService.createReservationOptimizedRedisson(userId, idempotencyKey, request);
                }
                return reservationService.createReservation(userId, idempotencyKey, request);
            } finally {
                long releasedNanos = System.nanoTime();
                long holdDurationNanos = releasedNanos - acquiredNanos;
                log.info("[LOCK_PROFILE] itemId={} acquired_nanos={} released_nanos={} hold_nanos={}",
                        itemId, acquiredNanos, releasedNanos, holdDurationNanos);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("Thread interrupted while waiting for lock", e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                long unlockStartNanos = System.nanoTime();
                try {
                    lock.unlock();
                } catch (Exception unlockEx) {
                    log.error("Failed to unlock Redisson lock for key: {}", lockKey, unlockEx);
                } finally {
                    long unlockNanos = System.nanoTime() - unlockStartNanos;
                    log.info("[UNLOCK_PROFILE] itemId={} unlock_nanos={}", itemId, unlockNanos);
                }
            }
        }
    }
}
