package com.b2c.flash_sale_b2c_UTC2.flashsale.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.EmergencyPauseRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.EmergencyPauseResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.RealtimeTelemetryResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import com.b2c.flash_sale_b2c_UTC2.flashsale.exception.FlashSaleErrorCode;
import com.b2c.flash_sale_b2c_UTC2.flashsale.mapper.FlashSaleSlotMapper;
import com.b2c.flash_sale_b2c_UTC2.flashsale.realtime.FlashSaleWsBroadcaster;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleSlotRepository;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderRepository;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlashSaleSlotServiceTest {

    @Mock
    private FlashSaleSlotRepository slotRepository;

    @Mock
    private FlashSaleSlotMapper slotMapper;

    @Mock
    private FlashSaleItemRepository itemRepository;

    @Mock
    private ProductVariantRepository variantRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private FlashSaleWsBroadcaster wsBroadcaster;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private FlashSaleSlotService slotService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(slotService, "orderRepository", orderRepository);
        ReflectionTestUtils.setField(slotService, "redisTemplate", redisTemplate);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    @DisplayName("getSlotTelemetry - throws SLOT_NOT_FOUND when slot does not exist")
    void getSlotTelemetry_SlotNotFound() {
        when(slotRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> slotService.getSlotTelemetry(99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", FlashSaleErrorCode.SLOT_NOT_FOUND);
    }

    @Test
    @DisplayName("getSlotTelemetry - returns telemetry successfully")
    void getSlotTelemetry_Success() {
        FlashSaleSlot slot = FlashSaleSlot.builder()
                .id(1L)
                .title("Flash Sale Trưa 12h")
                .status("ACTIVE")
                .isStockRolledBack(false)
                .build();

        when(slotRepository.findById(1L)).thenReturn(Optional.of(slot));
        when(valueOperations.get("flash_sale:slot:1:paused")).thenReturn(null);
        when(zSetOperations.zCard("flash_sale:pending_res_index")).thenReturn(5L);
        when(orderRepository.countSuccessfulOrdersBySlotId(1L)).thenReturn(120L);
        when(orderRepository.sumTotalRevenueBySlotId(1L)).thenReturn(new BigDecimal("12000000"));
        when(orderRepository.countExpiredOrdersBySlotId(1L)).thenReturn(3L);

        RealtimeTelemetryResponse response = slotService.getSlotTelemetry(1L);

        assertThat(response).isNotNull();
        assertThat(response.getSlotId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getIsPaused()).isFalse();
        assertThat(response.getActiveLocks()).isEqualTo(5L);
        assertThat(response.getSuccessfulOrders()).isEqualTo(120L);
        assertThat(response.getTotalRevenue()).isEqualTo(new BigDecimal("12000000"));
        assertThat(response.getOversoldIncidents()).isEqualTo(0);
    }

    @Test
    @DisplayName("emergencyPauseSlot - throws SLOT_NOT_FOUND when slot does not exist")
    void emergencyPauseSlot_SlotNotFound() {
        when(slotRepository.findById(99L)).thenReturn(Optional.empty());

        EmergencyPauseRequest request = EmergencyPauseRequest.builder()
                .reason("Test pause")
                .rollbackStock(true)
                .build();

        assertThatThrownBy(() -> slotService.emergencyPauseSlot(99L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", FlashSaleErrorCode.SLOT_NOT_FOUND);
    }

    @Test
    @DisplayName("emergencyPauseSlot - throws SLOT_NOT_ACTIVE when slot is not ACTIVE (e.g. UPCOMING or ENDED)")
    void emergencyPauseSlot_SlotNotActive() {
        FlashSaleSlot slot = FlashSaleSlot.builder()
                .id(1L)
                .status("UPCOMING")
                .isStockRolledBack(false)
                .build();

        when(slotRepository.findById(1L)).thenReturn(Optional.of(slot));

        EmergencyPauseRequest request = EmergencyPauseRequest.builder()
                .reason("Test pause")
                .rollbackStock(true)
                .build();

        assertThatThrownBy(() -> slotService.emergencyPauseSlot(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", FlashSaleErrorCode.SLOT_NOT_ACTIVE);
    }

    @Test
    @DisplayName("emergencyPauseSlot - success: marks PAUSED, rolls back stock, sets isStockRolledBack, broadcasts WS")
    void emergencyPauseSlot_Success_WithRollback() {
        FlashSaleSlot slot = FlashSaleSlot.builder()
                .id(1L)
                .status("ACTIVE")
                .isStockRolledBack(false)
                .build();

        ProductVariant variant1 = ProductVariant.builder().id(101L).sku("SKU-101").build();
        ProductVariant variant2 = ProductVariant.builder().id(102L).sku("SKU-102").build();

        FlashSaleItem item1 = FlashSaleItem.builder()
                .id(11L)
                .slot(slot)
                .variant(variant1)
                .status("APPROVED")
                .availableStock(20)
                .build();

        FlashSaleItem item2 = FlashSaleItem.builder()
                .id(12L)
                .slot(slot)
                .variant(variant2)
                .status("APPROVED")
                .availableStock(15)
                .build();

        when(slotRepository.findById(1L)).thenReturn(Optional.of(slot));
        when(itemRepository.findBySlotId(1L)).thenReturn(List.of(item1, item2));
        when(valueOperations.get("flash_sale:stock:11")).thenReturn("20");
        when(valueOperations.get("flash_sale:stock:12")).thenReturn("15");

        EmergencyPauseRequest request = EmergencyPauseRequest.builder()
                .reason("High load detected")
                .rollbackStock(true)
                .build();

        EmergencyPauseResponse response = slotService.emergencyPauseSlot(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("PAUSED");
        assertThat(response.getIsPaused()).isTrue();
        assertThat(response.getStockRolledBack()).isTrue();
        assertThat(response.getRolledBackItemsCount()).isEqualTo(2);
        assertThat(response.getTotalStockReturned()).isEqualTo(35);

        // Verify Redis pause flags set
        verify(valueOperations).set(eq("flash_sale:slot:1:paused"), eq("1"), any(java.time.Duration.class));
        verify(valueOperations).set(eq("flashsale:slot:1:paused"), eq("1"), any(java.time.Duration.class));

        // Verify stock replenished in DB
        verify(variantRepository).replenishStockQuantityConditionally(101L, 20);
        verify(variantRepository).replenishStockQuantityConditionally(102L, 15);

        // Verify Redis stock keys deleted
        verify(redisTemplate).delete("flash_sale:stock:11");
        verify(redisTemplate).delete("flash_sale:stock:12");

        // Verify WS broadcast
        verify(wsBroadcaster).broadcastSlotStatus(1L, "PAUSED");

        // Verify slot state saved with isStockRolledBack = true
        assertThat(slot.getStatus()).isEqualTo("PAUSED");
        assertThat(slot.getIsStockRolledBack()).isTrue();
        verify(slotRepository).save(slot);
    }

    @Test
    @DisplayName("emergencyPauseSlot - cannot pause twice / cannot rollback twice (SLOT_NOT_ACTIVE on second pause)")
    void emergencyPauseSlot_CannotPauseTwice() {
        FlashSaleSlot slot = FlashSaleSlot.builder()
                .id(1L)
                .status("PAUSED")
                .isStockRolledBack(true)
                .build();

        when(slotRepository.findById(1L)).thenReturn(Optional.of(slot));

        EmergencyPauseRequest request = EmergencyPauseRequest.builder()
                .reason("Trying to pause again")
                .rollbackStock(true)
                .build();

        assertThatThrownBy(() -> slotService.emergencyPauseSlot(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", FlashSaleErrorCode.SLOT_NOT_ACTIVE);

        // Verify no rollback attempted
        verify(variantRepository, never()).replenishStockQuantityConditionally(anyLong(), any());
    }
}
