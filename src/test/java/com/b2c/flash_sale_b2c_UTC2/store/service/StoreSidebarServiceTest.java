package com.b2c.flash_sale_b2c_UTC2.store.service;

import com.b2c.flash_sale_b2c_UTC2.store.dto.SidebarSummaryResponse;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.enums.StoreTier;
import com.b2c.flash_sale_b2c_UTC2.store.exception.StoreErrorCode;
import com.b2c.flash_sale_b2c_UTC2.store.mapper.StoreMapper;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleSlotRepository;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.review.repository.ProductReviewRepository;
import com.b2c.flash_sale_b2c_UTC2.wallet.repository.WalletRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.AddressRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.RoleRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRoleRepository;
import com.b2c.flash_sale_b2c_UTC2.image.repository.ImageRepository;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.user.mapper.AddressMapper;
import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreSidebarServiceTest {

    @Mock private StoreRepository storeRepository;
    @Mock private ProductRepository productRepository;
    @Mock private FlashSaleItemRepository flashSaleItemRepository;
    @Mock private FlashSaleSlotRepository flashSaleSlotRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private ProductReviewRepository productReviewRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private ImageRepository imageRepository;
    @Mock private StoreMapper storeMapper;
    @Mock private AddressMapper addressMapper;
    @Mock private ImageService imageService;

    @InjectMocks
    private StoreServiceImpl storeService;

    private Store buildStore(Long id, String status, StoreTier tier) {
        return Store.builder()
                .id(id)
                .storeName("Test Store")
                .status(status)
                .tier(tier)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Happy path: store with full metrics -- all fields populated")
    void getSidebarSummary_happyPath_allFieldsPopulated() {
        Long storeId = 1L;
        Long userId = 10L;
        Store store = buildStore(storeId, "APPROVED", StoreTier.PREFERRED);

        when(storeRepository.findByUserId(userId)).thenReturn(Optional.of(store));
        when(productRepository.countByStoreIdAndStatus(storeId, "ACTIVE")).thenReturn(42L);
        when(flashSaleItemRepository.countActiveByStoreId(storeId)).thenReturn(2L);
        when(orderRepository.countByStoreIdAndStatus(storeId, "PENDING_PAYMENT")).thenReturn(7L);
        when(orderRepository.countByStoreIdAndCreatedAtBetween(eq(storeId), any(Instant.class), any(Instant.class)))
                .thenReturn(12L);
        final int[] revenueCallCount = {0};
        when(orderRepository.sumTotalAmountByStoreIdAndCreatedAtBetweenAndStatusIn(
                eq(storeId), any(Instant.class), any(Instant.class), anyList()))
                .thenAnswer(invocation -> {
                    revenueCallCount[0]++;
                    return revenueCallCount[0] == 1
                            ? new BigDecimal("5290000")
                            : new BigDecimal("98000000");
                });
        when(productReviewRepository.avgRatingByStoreId(storeId)).thenReturn(4.6);
        when(productReviewRepository.countByStoreId(storeId)).thenReturn(183L);
        when(productVariantRepository.countByProductStoreIdAndStockQuantityLessThan(storeId, 10))
                .thenReturn(3L);

        SidebarSummaryResponse result = storeService.getSidebarSummary(userId);

        assertNotNull(result);
        assertEquals(42L, result.getTotalProducts());
        assertEquals(2L, result.getActiveFlashSales());
        assertEquals(7L, result.getPendingOrders());
        assertEquals(7L, result.getUnpaidOrders());
        assertEquals(12L, result.getTodayOrders());
        assertEquals(new BigDecimal("5290000"), result.getTodayRevenue());
        assertEquals(new BigDecimal("98000000"), result.getMonthRevenue());
        assertEquals(4.6, result.getRatingAvg());
        assertEquals(183L, result.getRatingCount());
        assertEquals(3L, result.getLowStockProducts());
        assertEquals("APPROVED", result.getStoreStatus());
        assertEquals("PREFERRED", result.getTier());
    }

    @Test
    @DisplayName("Edge: new store with 0 products, 0 orders -- all counters 0, revenue 0")
    void getSidebarSummary_newStore_allCountersZero() {
        Long storeId = 2L;
        Long userId = 20L;
        Store store = buildStore(storeId, "PENDING", StoreTier.NORMAL);

        when(storeRepository.findByUserId(userId)).thenReturn(Optional.of(store));
        when(productRepository.countByStoreIdAndStatus(storeId, "ACTIVE")).thenReturn(0L);
        when(flashSaleItemRepository.countActiveByStoreId(storeId)).thenReturn(0L);
        when(orderRepository.countByStoreIdAndStatus(storeId, "PENDING_PAYMENT")).thenReturn(0L);
        when(orderRepository.countByStoreIdAndCreatedAtBetween(eq(storeId), any(), any())).thenReturn(0L);
        when(orderRepository.sumTotalAmountByStoreIdAndCreatedAtBetweenAndStatusIn(
                eq(storeId), any(), any(), anyList())).thenReturn(null);
        when(productReviewRepository.avgRatingByStoreId(storeId)).thenReturn(null);
        when(productReviewRepository.countByStoreId(storeId)).thenReturn(0L);
        when(productVariantRepository.countByProductStoreIdAndStockQuantityLessThan(storeId, 10)).thenReturn(0L);

        SidebarSummaryResponse result = storeService.getSidebarSummary(userId);

        assertNotNull(result);
        assertEquals(0L, result.getTotalProducts());
        assertEquals(0L, result.getActiveFlashSales());
        assertEquals(0L, result.getPendingOrders());
        assertEquals(0L, result.getUnpaidOrders());
        assertEquals(0L, result.getTodayOrders());
        assertEquals(BigDecimal.ZERO, result.getTodayRevenue());
        assertEquals(BigDecimal.ZERO, result.getMonthRevenue());
        assertEquals(0.0, result.getRatingAvg());
        assertEquals(0L, result.getRatingCount());
        assertEquals(0L, result.getLowStockProducts());
        assertEquals("PENDING", result.getStoreStatus());
        assertEquals("NORMAL", result.getTier());
    }

    @Test
    @DisplayName("Edge: store has no reviews -- ratingAvg=0.0, ratingCount=0")
    void getSidebarSummary_noReviews_ratingZero() {
        Long storeId = 4L;
        Long userId = 40L;
        Store store = buildStore(storeId, "APPROVED", StoreTier.NORMAL);

        when(storeRepository.findByUserId(userId)).thenReturn(Optional.of(store));
        when(productRepository.countByStoreIdAndStatus(storeId, "ACTIVE")).thenReturn(1L);
        when(flashSaleItemRepository.countActiveByStoreId(storeId)).thenReturn(0L);
        when(orderRepository.countByStoreIdAndStatus(storeId, "PENDING_PAYMENT")).thenReturn(0L);
        when(orderRepository.countByStoreIdAndCreatedAtBetween(eq(storeId), any(), any())).thenReturn(0L);
        when(orderRepository.sumTotalAmountByStoreIdAndCreatedAtBetweenAndStatusIn(
                eq(storeId), any(), any(), anyList())).thenReturn(null);
        when(productReviewRepository.avgRatingByStoreId(storeId)).thenReturn(null);
        when(productReviewRepository.countByStoreId(storeId)).thenReturn(0L);
        when(productVariantRepository.countByProductStoreIdAndStockQuantityLessThan(storeId, 10)).thenReturn(0L);

        SidebarSummaryResponse result = storeService.getSidebarSummary(userId);

        assertNotNull(result);
        assertEquals(0.0, result.getRatingAvg());
        assertEquals(0L, result.getRatingCount());
    }

    @Test
    @DisplayName("Error: user has no store -- throws STORE_NOT_FOUND")
    void getSidebarSummary_noStore_throwsStoreNotFound() {
        Long userId = 99L;
        when(storeRepository.findByUserId(userId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> storeService.getSidebarSummary(userId));
        assertEquals(StoreErrorCode.STORE_NOT_FOUND, ex.getErrorCode());
    }
}