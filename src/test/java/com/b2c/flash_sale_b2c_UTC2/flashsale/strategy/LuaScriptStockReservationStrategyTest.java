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
    @DisplayName("reserveStock trꏂ„㄀ 欀栀椀 䰀甀愀 猀甀挀挀攀猀猀∀⤀਀    瘀漀椀搀 爀攀猀攀爀瘀攀匀琀漀挀欀开匀甀挀挀攀猀猀⠀⤀ 笀਀        眀栀攀渀⠀爀攀搀椀猀吀攀洀瀀氀愀琀攀⸀攀砀攀挀甀琀攀⠀愀渀礀⠀刀攀搀椀猀匀挀爀椀瀀琀⸀挀氀愀猀猀⤀Ⰰ 愀渀礀䰀椀猀琀⠀⤀Ⰰ 愀渀礀⠀伀戀樀攀挀琀嬀崀⸀挀氀愀猀猀⤀⤀⤀਀                ⸀琀栀攀渀刀攀琀甀爀渀⠀㄀䰀⤀㬀਀਀        氀漀渀最 爀攀猀甀氀琀 㴀 猀琀爀愀琀攀最礀⸀爀攀猀攀爀瘀攀匀琀漀挀欀⠀㄀䰀Ⰰ ㄀䰀Ⰰ 㤀㤀䰀Ⰰ ㄀Ⰰ ㈀Ⰰ ㌀㘀　　⤀㬀਀਀        愀猀猀攀爀琀䔀焀甀愀氀猀⠀㄀䰀Ⰰ 爀攀猀甀氀琀⤀㬀਀    紀਀਀    䀀吀攀猀琀਀    䀀䐀椀猀瀀氀愀礀一愀洀攀⠀∀爀攀猀攀爀瘀攀匀琀漀挀欀 琀爀숀ả 0 khi Lua trꏂ„　 ⠀漀甀琀 漀昀 猀琀漀挀欀⤀∀⤀਀    瘀漀椀搀 爀攀猀攀爀瘀攀匀琀漀挀欀开伀甀琀伀昀匀琀漀挀欀⠀⤀ 笀਀        眀栀攀渀⠀爀攀搀椀猀吀攀洀瀀氀愀琀攀⸀攀砀攀挀甀琀攀⠀愀渀礀⠀刀攀搀椀猀匀挀爀椀瀀琀⸀挀氀愀猀猀⤀Ⰰ 愀渀礀䰀椀猀琀⠀⤀Ⰰ 愀渀礀⠀伀戀樀攀挀琀嬀崀⸀挀氀愀猀猀⤀⤀⤀਀                ⸀琀栀攀渀刀攀琀甀爀渀⠀　䰀⤀㬀਀਀        氀漀渀最 爀攀猀甀氀琀 㴀 猀琀爀愀琀攀最礀⸀爀攀猀攀爀瘀攀匀琀漀挀欀⠀㄀䰀Ⰰ ㄀䰀Ⰰ 㤀㤀䰀Ⰰ ㄀Ⰰ ㈀Ⰰ ㌀㘀　　⤀㬀਀਀        愀猀猀攀爀琀䔀焀甀愀氀猀⠀　䰀Ⰰ 爀攀猀甀氀琀⤀㬀਀    紀਀਀    䀀吀攀猀琀਀    䀀䐀椀猀瀀氀愀礀一愀洀攀⠀∀爀攀猀攀爀瘀攀匀琀漀挀欀 琀爀숀ả -1 khi Lua trꏂ„ⴀ㄀ ⠀甀猀攀爀 氀椀洀椀琀 攀砀挀攀攀搀攀搀⤀∀⤀਀    瘀漀椀搀 爀攀猀攀爀瘀攀匀琀漀挀欀开唀猀攀爀䰀椀洀椀琀䔀砀挀攀攀搀攀搀⠀⤀ 笀਀        眀栀攀渀⠀爀攀搀椀猀吀攀洀瀀氀愀琀攀⸀攀砀攀挀甀琀攀⠀愀渀礀⠀刀攀搀椀猀匀挀爀椀瀀琀⸀挀氀愀猀猀⤀Ⰰ 愀渀礀䰀椀猀琀⠀⤀Ⰰ 愀渀礀⠀伀戀樀攀挀琀嬀崀⸀挀氀愀猀猀⤀⤀⤀਀                ⸀琀栀攀渀刀攀琀甀爀渀⠀ⴀ㄀䰀⤀㬀਀਀        氀漀渀最 爀攀猀甀氀琀 㴀 猀琀爀愀琀攀最礀⸀爀攀猀攀爀瘀攀匀琀漀挀欀⠀㄀䰀Ⰰ ㄀䰀Ⰰ 㤀㤀䰀Ⰰ ㄀Ⰰ ㈀Ⰰ ㌀㘀　　⤀㬀਀਀        愀猀猀攀爀琀䔀焀甀愀氀猀⠀ⴀ㄀䰀Ⰰ 爀攀猀甀氀琀⤀㬀਀    紀਀਀    䀀吀攀猀琀਀    䀀䐀椀猀瀀氀愀礀一愀洀攀⠀∀爀攀猀攀爀瘀攀匀琀漀挀欀 琀爀숀ả 0 khi Lua null ho럂挞 ⴀ㈀∀⤀਀    瘀漀椀搀 爀攀猀攀爀瘀攀匀琀漀挀欀开䠀愀渀搀氀攀猀一甀氀氀䄀渀搀一攀最愀琀椀瘀攀吀眀漀⠀⤀ 笀਀        眀栀攀渀⠀爀攀搀椀猀吀攀洀瀀氀愀琀攀⸀攀砀攀挀甀琀攀⠀愀渀礀⠀刀攀搀椀猀匀挀爀椀瀀琀⸀挀氀愀猀猀⤀Ⰰ 愀渀礀䰀椀猀琀⠀⤀Ⰰ 愀渀礀⠀伀戀樀攀挀琀嬀崀⸀挀氀愀猀猀⤀⤀⤀਀                ⸀琀栀攀渀刀攀琀甀爀渀⠀渀甀氀氀⤀㬀਀        愀猀猀攀爀琀䔀焀甀愀氀猀⠀　䰀Ⰰ 猀琀爀愀琀攀最礀⸀爀攀猀攀爀瘀攀匀琀漀挀欀⠀㄀䰀Ⰰ ㄀䰀Ⰰ 㤀㤀䰀Ⰰ ㄀Ⰰ ㈀Ⰰ ㌀㘀　　⤀⤀㬀਀਀        眀栀攀渀⠀爀攀搀椀猀吀攀洀瀀氀愀琀攀⸀攀砀攀挀甀琀攀⠀愀渀礀⠀刀攀搀椀猀匀挀爀椀瀀琀⸀挀氀愀猀猀⤀Ⰰ 愀渀礀䰀椀猀琀⠀⤀Ⰰ 愀渀礀⠀伀戀樀攀挀琀嬀崀⸀挀氀愀猀猀⤀⤀⤀਀                ⸀琀栀攀渀刀攀琀甀爀渀⠀ⴀ㈀䰀⤀㬀਀        愀猀猀攀爀琀䔀焀甀愀氀猀⠀　䰀Ⰰ 猀琀爀愀琀攀最礀⸀爀攀猀攀爀瘀攀匀琀漀挀欀⠀㄀䰀Ⰰ ㄀䰀Ⰰ 㤀㤀䰀Ⰰ ㄀Ⰰ ㈀Ⰰ ㌀㘀　　⤀⤀㬀਀    紀਀਀    䀀吀攀猀琀਀    䀀䐀椀猀瀀氀愀礀一愀洀攀⠀∀爀攀猀攀爀瘀攀匀琀漀挀欀 爀攀ⴀ琀栀爀漀眀 攀砀挀攀瀀琀椀漀渀 琀쌀ẫ Redis")
    void reserveStock_ReThrowsException() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("Redis down"));

        assertThrows(RuntimeException.class,
                () -> strategy.reserveStock(1L, 1L, 99L, 1, 2, 3600));
    }

    // ============== compensate ==============

    @Test
    @DisplayName("compensate INCRBY stock vꃃ 䐀䔀䌀刀䈀夀 甀猀攀爀开氀椀洀椀琀∀⤀਀    瘀漀椀搀 挀漀洀瀀攀渀猀愀琀攀开匀甀挀挀攀猀猀⠀⤀ 笀਀        眀栀攀渀⠀爀攀搀椀猀吀攀洀瀀氀愀琀攀⸀漀瀀猀䘀漀爀嘀愀氀甀攀⠀⤀⤀⸀琀栀攀渀刀攀琀甀爀渀⠀瘀愀氀甀攀伀瀀攀爀愀琀椀漀渀猀⤀㬀਀        眀栀攀渀⠀瘀愀氀甀攀伀瀀攀爀愀琀椀漀渀猀⸀搀攀挀爀攀洀攀渀琀⠀愀渀礀匀琀爀椀渀最⠀⤀Ⰰ 愀渀礀䰀漀渀最⠀⤀⤀⤀⸀琀栀攀渀刀攀琀甀爀渀⠀㄀䰀⤀㬀਀਀        猀琀爀愀琀攀最礀⸀挀漀洀瀀攀渀猀愀琀攀⠀㄀䰀Ⰰ ㄀䰀Ⰰ 㤀㤀䰀Ⰰ ㈀⤀㬀਀਀        瘀攀爀椀昀礀⠀瘀愀氀甀攀伀瀀攀爀愀琀椀漀渀猀⤀⸀椀渀挀爀攀洀攀渀琀⠀∀昀氀愀猀栀开猀愀氀攀㨀猀琀漀挀欀㨀㤀㤀∀Ⰰ ㈀⤀㬀਀        瘀攀爀椀昀礀⠀瘀愀氀甀攀伀瀀攀爀愀琀椀漀渀猀⤀⸀搀攀挀爀攀洀攀渀琀⠀∀昀氀愀猀栀开猀愀氀攀㨀甀猀攀爀开氀椀洀椀琀㨀㄀㨀㄀㨀㤀㤀∀Ⰰ ㈀⤀㬀਀    紀਀਀    䀀吀攀猀琀਀    䀀䐀椀猀瀀氀愀礀一愀洀攀⠀∀挀漀洀瀀攀渀猀愀琀攀 砀쌀³a user_limit key khi remaining <= 0")
    void compensate_DeletesKey_WhenLimitReachedZero() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.decrement(anyString(), anyLong())).thenReturn(0L);

        strategy.compensate(1L, 1L, 99L, 2);

        verify(redisTemplate).delete("flash_sale:user_limit:1:1:99");
    }

    @Test
    @DisplayName("compensate kh듃渀最 琀栀爀漀眀 欀栀椀 刀攀搀椀猀 攀砀挀攀瀀琀椀漀渀 ⠀昀愀椀氀ⴀ猀愀昀攀⤀∀⤀਀    瘀漀椀搀 挀漀洀瀀攀渀猀愀琀攀开䐀漀攀猀一漀琀吀栀爀漀眀开伀渀䔀砀挀攀瀀琀椀漀渀⠀⤀ 笀਀        眀栀攀渀⠀爀攀搀椀猀吀攀洀瀀氀愀琀攀⸀漀瀀猀䘀漀爀嘀愀氀甀攀⠀⤀⤀⸀琀栀攀渀吀栀爀漀眀⠀渀攀眀 刀甀渀琀椀洀攀䔀砀挀攀瀀琀椀漀渀⠀∀刀攀搀椀猀 搀漀眀渀∀⤀⤀㬀਀਀        愀猀猀攀爀琀䐀漀攀猀一漀琀吀栀爀漀眀⠀⠀⤀ ⴀ㸀 猀琀爀愀琀攀最礀⸀挀漀洀瀀攀渀猀愀琀攀⠀㄀䰀Ⰰ ㄀䰀Ⰰ 㤀㤀䰀Ⰰ ㈀⤀⤀㬀਀    紀਀਀    ⼀⼀ 㴀㴀㴀㴀㴀㴀㴀㴀㴀㴀㴀㴀㴀㴀 爀漀氀氀戀愀挀欀匀琀漀挀欀匀愀昀攀 㴀㴀㴀㴀㴀㴀㴀㴀㴀㴀㴀㴀㴀㴀਀਀    䀀吀攀猀琀਀    䀀䐀椀猀瀀氀愀礀一愀洀攀⠀∀爀漀氀氀戀愀挀欀匀琀漀挀欀匀愀昀攀 最쌀ẍi rollback script vꃃ 搀攀挀爀攀洀攀渀琀 甀猀攀爀开氀椀洀椀琀∀⤀਀    瘀漀椀搀 爀漀氀氀戀愀挀欀匀琀漀挀欀匀愀昀攀开匀甀挀挀攀猀猀⠀⤀ 笀਀        眀栀攀渀⠀爀攀搀椀猀吀攀洀瀀氀愀琀攀⸀攀砀攀挀甀琀攀⠀愀渀礀⠀刀攀搀椀猀匀挀爀椀瀀琀⸀挀氀愀猀猀⤀Ⰰ 愀渀礀䰀椀猀琀⠀⤀Ⰰ 愀渀礀⠀伀戀樀攀挀琀嬀崀⸀挀氀愀猀猀⤀⤀⤀਀                ⸀琀栀攀渀刀攀琀甀爀渀⠀㄀　䰀⤀㬀਀        眀栀攀渀⠀爀攀搀椀猀吀攀洀瀀氀愀琀攀⸀漀瀀猀䘀漀爀嘀愀氀甀攀⠀⤀⤀⸀琀栀攀渀刀攀琀甀爀渀⠀瘀愀氀甀攀伀瀀攀爀愀琀椀漀渀猀⤀㬀਀        眀栀攀渀⠀瘀愀氀甀攀伀瀀攀爀愀琀椀漀渀猀⸀搀攀挀爀攀洀攀渀琀⠀愀渀礀匀琀爀椀渀最⠀⤀Ⰰ 愀渀礀䰀漀渀最⠀⤀⤀⤀⸀琀栀攀渀刀攀琀甀爀渀⠀　䰀⤀㬀਀਀        猀琀爀愀琀攀最礀⸀爀漀氀氀戀愀挀欀匀琀漀挀欀匀愀昀攀⠀㄀䰀Ⰰ ㄀䰀Ⰰ 㤀㤀䰀Ⰰ ㈀Ⰰ ㄀　Ⰰ ㌀㘀　　⤀㬀਀਀        瘀攀爀椀昀礀⠀爀攀搀椀猀吀攀洀瀀氀愀琀攀⤀⸀攀砀攀挀甀琀攀⠀愀渀礀⠀刀攀搀椀猀匀挀爀椀瀀琀⸀挀氀愀猀猀⤀Ⰰ 攀焀⠀䰀椀猀琀⸀漀昀⠀∀昀氀愀猀栀开猀愀氀攀㨀猀琀漀挀欀㨀㤀㤀∀⤀⤀Ⰰ਀                攀焀⠀∀㈀∀⤀Ⰰ 攀焀⠀∀㄀　∀⤀Ⰰ 攀焀⠀∀㌀㘀　　∀⤀⤀㬀਀    紀਀਀    䀀吀攀猀琀਀    䀀䐀椀猀瀀氀愀礀一愀洀攀⠀∀爀漀氀氀戀愀挀欀匀琀漀挀欀匀愀昀攀 昀愀氀氀戀愀挀欀 吀吀䰀 㴀 㘀　 欀栀椀 爀攀洀愀椀渀椀渀最 吀吀䰀 㰀 　∀⤀਀    瘀漀椀搀 爀漀氀氀戀愀挀欀匀琀漀挀欀匀愀昀攀开䌀氀愀洀瀀猀吀琀氀吀漀䴀椀渀椀洀甀洀⠀⤀ 笀਀        眀栀攀渀⠀爀攀搀椀猀吀攀洀瀀氀愀琀攀⸀攀砀攀挀甀琀攀⠀愀渀礀⠀刀攀搀椀猀匀挀爀椀瀀琀⸀挀氀愀猀猀⤀Ⰰ 愀渀礀䰀椀猀琀⠀⤀Ⰰ 愀渀礀⠀伀戀樀攀挀琀嬀崀⸀挀氀愀猀猀⤀⤀⤀਀                ⸀琀栀攀渀刀攀琀甀爀渀⠀㔀䰀⤀㬀਀        眀栀攀渀⠀爀攀搀椀猀吀攀洀瀀氀愀琀攀⸀漀瀀猀䘀漀爀嘀愀氀甀攀⠀⤀⤀⸀琀栀攀渀刀攀琀甀爀渀⠀瘀愀氀甀攀伀瀀攀爀愀琀椀漀渀猀⤀㬀਀        眀栀攀渀⠀瘀愀氀甀攀伀瀀攀爀愀琀椀漀渀猀⸀搀攀挀爀攀洀攀渀琀⠀愀渀礀匀琀爀椀渀最⠀⤀Ⰰ 愀渀礀䰀漀渀最⠀⤀⤀⤀⸀琀栀攀渀刀攀琀甀爀渀⠀　䰀⤀㬀਀਀        猀琀爀愀琀攀最礀⸀爀漀氀氀戀愀挀欀匀琀漀挀欀匀愀昀攀⠀㄀䰀Ⰰ ㄀䰀Ⰰ 㤀㤀䰀Ⰰ ㈀Ⰰ 㔀Ⰰ ⴀ㄀　⤀㬀 ⼀⼀ 吀吀䰀 쌀¢m 胢↙ clamp v臃„㘀　਀਀        瘀攀爀椀昀礀⠀爀攀搀椀猀吀攀洀瀀氀愀琀攀⤀⸀攀砀攀挀甀琀攀⠀愀渀礀⠀刀攀搀椀猀匀挀爀椀瀀琀⸀挀氀愀猀猀⤀Ⰰ 愀渀礀䰀椀猀琀⠀⤀Ⰰ਀                攀焀⠀∀㈀∀⤀Ⰰ 攀焀⠀∀㔀∀⤀Ⰰ 攀焀⠀∀㘀　∀⤀⤀㬀਀    紀਀਀    䀀吀攀猀琀਀    䀀䐀椀猀瀀氀愀礀一愀洀攀⠀∀爀漀氀氀戀愀挀欀匀琀漀挀欀匀愀昀攀 欀栀쌀´ng throw khi Redis exception")
    void rollbackStockSafe_DoesNotThrow_OnException() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("Redis down"));

        assertDoesNotThrow(() -> strategy.rollbackStockSafe(1L, 1L, 99L, 2, 5, 3600));
    }
}