package com.b2c.flash_sale_b2c_UTC2.flashsale.service;

import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.FlashSaleOrderPort;
import com.b2c.flash_sale_b2c_UTC2.flashsale.realtime.FlashSaleWsBroadcaster;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleSlotRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for FlashSaleSlotCloseService.
 *
 * Background: previously the closeSlotAndReturnStock method lived inside
 * FlashSaleExpirationScheduler. The scheduler's outer method is NOT
 * @Transactional, and a method calling another @Transactional method
 * inside the same class via `this` bypasses the Spring AOP proxy —
 * so when the inner method hit a @Modifying UPDATE on
 * ProductVariantRepository, Hibernate threw TransactionRequiredException.
 *
 * After the fix, the logic lives in a separate @Service bean
 * (FlashSaleSlotCloseService) with @Transactional on the public method.
 * Calls go through the proxy, so a transaction is active.
 *
 * These tests assert the new service's behavior in isolation, without
 * relying on Spring's transaction infrastructure, so they cannot
 * accidentally regress into the same self-invocation bug.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlashSaleSlotCloseServiceTest {

    @Mock private FlashSaleSlotRepository slotRepository;
    @Mock private FlashSaleItemRepository itemRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private FlashSaleWsBroadcaster wsBroadcaster;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private FlashSaleOrderPort orderPort;

    @InjectMocks private FlashSaleSlotCloseService service;

    private FlashSaleSlot mkSlot(Long id, String status) {
        return FlashSaleSlot.builder()
                .id(id)
                .title("Slot " + id)
                .status(status)
                .startTime(Instant.now().minusSeconds(3600))
                .endTime(Instant.now().minusSeconds(60))
                .build();
    }

    private FlashSaleItem mkItem(Long id, FlashSaleSlot slot, ProductVariant variant,
                                 int availableStock, String status) {
        return FlashSaleItem.builder()
                .id(id)
                .slot(slot)
                .variant(variant)
                .availableStock(availableStock)
                .status(status)
                .build();
    }

    private ProductVariant mkVariant(Long id) {
        return ProductVariant.builder()
                .id(id)
                .originalPrice(java.math.BigDecimal.valueOf(100000))
                .stockQuantity(50)
                .status("ACTIVE")
                .build();
    }

    @BeforeEach
    void resetMocks() {
        ReflectionTestUtils.setField(service, "orderPort", orderPort);
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);
        when(orderPort.countPendingBySlot(any())).thenReturn(0L);
    }

    @Test
    @DisplayName("slot with APPROVED items: replenishStockQuantityConditionally is called with reserved qty")
    void close_returnsUnsoldStockToVariant() {
        FlashSaleSlot slot = mkSlot(1L, "ACTIVE");
        ProductVariant v = mkVariant(10L);
        FlashSaleItem item = mkItem(100L, slot, v, 7, "APPROVED");

        when(itemRepository.findBySlotId(slot.getId())).thenReturn(List.of(item));

        service.closeAndReturnStock(slot);

        verify(variantRepository, times(1))
                .replenishStockQuantityConditionally(eq(10L), eq(7));
        verify(itemRepository).save(any(FlashSaleItem.class));
        verify(slotRepository).save(any(FlashSaleSlot.class));
    }

    @Test
    @DisplayName("item with no unsold stock: replenish is NOT called (avoid zero-effect UPDATE)")
    void close_zeroUnsoldStock_doesNotReplenish() {
        FlashSaleSlot slot = mkSlot(1L, "ACTIVE");
        ProductVariant v = mkVariant(10L);
        FlashSaleItem item = mkItem(100L, slot, v, 0, "APPROVED");

        when(itemRepository.findBySlotId(slot.getId())).thenReturn(List.of(item));

        service.closeAndReturnStock(slot);

        verify(variantRepository, never())
                .replenishStockQuantityConditionally(anyLong(), any(Integer.class));
    }

    @Test
    @DisplayName("item with non-APPROVED status: skip and do not touch variant stock")
    void close_nonApprovedItem_isSkipped() {
        FlashSaleSlot slot = mkSlot(1L, "ACTIVE");
        ProductVariant v = mkVariant(10L);
        FlashSaleItem item = mkItem(100L, slot, v, 5, "PENDING");

        when(itemRepository.findBySlotId(slot.getId())).thenReturn(List.of(item));

        service.closeAndReturnStock(slot);

        verify(variantRepository, never())
                .replenishStockQuantityConditionally(anyLong(), any(Integer.class));
    }

    @Test
    @DisplayName("slot still has pending reservations: abort close, do not touch stock")
    void close_slotWithPendingReservations_aborts() {
        FlashSaleSlot slot = mkSlot(1L, "ACTIVE");
        when(orderPort.countPendingBySlot(slot.getId())).thenReturn(3L);

        service.closeAndReturnStock(slot);

        verify(itemRepository, never()).findBySlotId(any());
        verify(variantRepository, never())
                .replenishStockQuantityConditionally(anyLong(), any(Integer.class));
        verify(slotRepository, never()).save(any(FlashSaleSlot.class));
    }
}
