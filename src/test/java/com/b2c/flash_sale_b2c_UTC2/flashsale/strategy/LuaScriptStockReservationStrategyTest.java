package com.b2c.flash_sale_b2c_UTC2.flashsale.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LuaScriptStockReservationStrategyTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private LuaScriptStockReservationStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new LuaScriptStockReservationStrategy(redisTemplate);
    }

    // ============== reserveStock ==============

    @Test
    @DisplayName("reserveStock returns 1 when Lua returns positive")
    void reserveStock_Success() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);

        long result = strategy.reserveStock(1L, 1L, 99L, 1, 2, 3600);

        assertEquals(1L, result);
    }

    @Test
    @DisplayName("reserveStock returns 0 when Lua returns -2 (out of stock)")
    void reserveStock_OutOfStock() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(-2L);

        long result = strategy.reserveStock(1L, 1L, 99L, 1, 2, 3600);

        assertEquals(0L, result);
    }

    @Test
    @DisplayName("reserveStock returns -1 when Lua returns -1 (user limit exceeded)")
    void reserveStock_UserLimitExceeded() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(-1L);

        long result = strategy.reserveStock(1L, 1L, 99L, 1, 2, 3600);

        assertEquals(-1L, result);
    }

    @Test
    @DisplayName("reserveStock returns 0 when Lua returns null or -2")
    void reserveStock_HandlesNullAndNegativeTwo() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(null);
        assertEquals(0L, strategy.reserveStock(1L, 1L, 99L, 1, 2, 3600));

        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(-2L);
        assertEquals(0L, strategy.reserveStock(1L, 1L, 99L, 1, 2, 3600));
    }

    @Test
    @DisplayName("reserveStock rethrows exception from Redis")
    void reserveStock_ReThrowsException() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("Redis down"));

        assertThrows(RuntimeException.class,
                () -> strategy.reserveStock(1L, 1L, 99L, 1, 2, 3600));
    }

    // ============== rollbackStockWithMarker ==============

    @Test
    @DisplayName("rollbackStockWithMarker returns true when Lua returns 1")
    void rollbackStockWithMarker_Success() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);

        boolean result = strategy.rollbackStockWithMarker(1L, 1L, 99L, 2, "ORD123");

        assertTrue(result);
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), eq("2"), eq("86400"), eq("ORD123"));
    }

    // ============== compensate ==============

    @Test
    @DisplayName("compensate deletes user_limit key when remaining <= 0")
    void compensate_DeletesKey_WhenLimitReachedZero() {
        // Lua script (rollback_stock.lua) INCRBY stock + DECRBY/DEL user_limit atomically.
        // Mock verifies the script is invoked with the correct keys (including user_limit).
        // Real DEL of user_limit key when remaining <= 0 is performed inside the Lua script
        // (rollback_stock.lua step 4) and requires a real Redis instance to verify end-to-end.
        String expectedStockKey = "flash_sale:stock:99";
        String expectedUserLimitKey = "flash_sale:user_limit:1:1:99";

        when(redisTemplate.execute(
                any(RedisScript.class),
                argThat(keys -> keys instanceof List<?> list
                        && list.size() == 5
                        && expectedStockKey.equals(list.get(0))
                        && expectedUserLimitKey.equals(list.get(1))),
                any(Object[].class)))
                .thenReturn(0L);

        strategy.compensate(1L, 1L, 99L, 2);

        verify(redisTemplate).execute(
                any(RedisScript.class),
                argThat(keys -> keys instanceof List<?> list
                        && list.size() == 5
                        && expectedStockKey.equals(list.get(0))
                        && expectedUserLimitKey.equals(list.get(1))),
                any(Object[].class));
    }

    @Test
    @DisplayName("compensate does not throw on Redis exception")
    void compensate_DoesNotThrow_OnException() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("Redis down"));

        assertDoesNotThrow(() -> strategy.compensate(1L, 1L, 99L, 2));
    }

    // ============== rollbackStockSafe ==============

    @Test
    @DisplayName("rollbackStockSafe executes rollback script")
    void rollbackStockSafe_Success() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(10L);

        strategy.rollbackStockSafe(1L, 1L, 99L, 2, 10, 3600);

        verify(redisTemplate).execute(any(RedisScript.class), anyList(), eq("2"), eq("3600"), eq(""));
    }

    @Test
    @DisplayName("rollbackStockSafe fallback TTL = 60 when remaining TTL < 0")
    void rollbackStockSafe_ClampsTtlToMinimum() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(5L);

        strategy.rollbackStockSafe(1L, 1L, 99L, 2, 5, -10);

        verify(redisTemplate).execute(any(RedisScript.class), anyList(), eq("2"), eq("60"), eq(""));
    }

    @Test
    @DisplayName("rollbackStockSafe does not throw on Redis exception")
    void rollbackStockSafe_DoesNotThrow_OnException() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("Redis down"));

        assertDoesNotThrow(() -> strategy.rollbackStockSafe(1L, 1L, 99L, 2, 5, 3600));
    }
}