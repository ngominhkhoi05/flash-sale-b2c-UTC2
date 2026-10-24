package com.b2c.flash_sale_b2c_UTC2.search.service;

import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.review.repository.ProductReviewRepository;
import com.b2c.flash_sale_b2c_UTC2.search.dto.SearchIndexDocument;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.enums.StoreTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductIndexerTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ProductReviewRepository productReviewRepository;

    @Mock
    private FlashSaleItemRepository flashSaleItemRepository;

    @Mock
    private SearchIndexService searchIndexService;

    @InjectMocks
    private ProductIndexer productIndexer;

    private Store approvedStore;
    private Category category;

    private void setUpData() {
        approvedStore = Store.builder()
                .id(100L)
                .storeName("Shop A")
                .status("APPROVED")
                .tier(StoreTier.OFFICIAL_MALL)
                .build();
        category = Category.builder().id(7).name("Audio").slug("audio").build();
    }

    private Product activeProduct(Long id) {
        setUpData();
        return Product.builder()
                .id(id)
                .store(approvedStore)
                .category(category)
                .name("Tai nghe Bluetooth " + id)
                .status("ACTIVE")
                .totalSold(20)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-02T00:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("indexProduct: sản phẩm ACTIVE -> bulk upsert 1 document với đầy đủ field denormalize")
    void indexProduct_activeProduct_upsertsDocument() {
        Product product = activeProduct(1L);
        when(productRepository.findByIdWithStoreAndCategory(1L)).thenReturn(Optional.of(product));
        when(productVariantRepository.aggregatePriceAndStockByProductIds(any()))
                .thenReturn(List.<Object[]>of(new Object[]{1L, new BigDecimal("1290000"), new BigDecimal("1990000"), 100}));
        when(productReviewRepository.aggregateRatingByProductIds(any()))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 4.5d, 30L}));
        when(flashSaleItemRepository.findProductIdsWithActiveFlashSale(any())).thenReturn(List.of(1L));
        when(searchIndexService.bulkUpsert(anyList())).thenReturn(1);

        boolean indexed = productIndexer.indexProduct(1L);

        assertTrue(indexed);
        ArgumentCaptor<List<SearchIndexDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(searchIndexService).bulkUpsert(captor.capture());
        SearchIndexDocument doc = captor.getValue().get(0);
        assertEquals(1L, doc.getId());
        assertEquals("Tai nghe Bluetooth 1", doc.getName());
        assertEquals(100L, doc.getStoreId());
        assertEquals("OFFICIAL_MALL", doc.getStoreTier());
        assertEquals(7, doc.getCategoryId());
        assertEquals("audio", doc.getCategorySlug());
        assertEquals(new BigDecimal("1290000"), doc.getMinPrice());
        assertEquals(new BigDecimal("1990000"), doc.getMaxPrice());
        assertEquals(100, doc.getTotalStock());
        assertEquals(20, doc.getTotalSold());
        assertEquals(4.5d, doc.getRatingAvg());
        assertEquals(30, doc.getRatingCount());
        assertTrue(doc.getIsFlashSale());
        assertEquals("ACTIVE", doc.getStatus());
    }

    @Test
    @DisplayName("indexProduct: sản phẩm không có variant/review -> minPrice=0, ratingAvg=0, ratingCount=0")
    void indexProduct_noVariantsOrReviews_usesZeroDefaults() {
        Product product = activeProduct(2L);
        when(productRepository.findByIdWithStoreAndCategory(2L)).thenReturn(Optional.of(product));
        when(productVariantRepository.aggregatePriceAndStockByProductIds(any())).thenReturn(List.of());
        when(productReviewRepository.aggregateRatingByProductIds(any())).thenReturn(List.of());
        when(flashSaleItemRepository.findProductIdsWithActiveFlashSale(any())).thenReturn(List.of());
        when(searchIndexService.bulkUpsert(anyList())).thenReturn(1);

        assertTrue(productIndexer.indexProduct(2L));

        ArgumentCaptor<List<SearchIndexDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(searchIndexService).bulkUpsert(captor.capture());
        SearchIndexDocument doc = captor.getValue().get(0);
        assertEquals(BigDecimal.ZERO, doc.getMinPrice());
        assertEquals(BigDecimal.ZERO, doc.getMaxPrice());
        assertEquals(0, doc.getTotalStock());
        assertEquals(0.0d, doc.getRatingAvg());
        assertEquals(0, doc.getRatingCount());
        assertFalse(doc.getIsFlashSale());
    }

    @Test
    @DisplayName("indexProduct: sản phẩm INACTIVE -> xoá document, KHÔNG upsert")
    void indexProduct_inactiveProduct_deletesDocument() {
        Product product = activeProduct(3L);
        product.setStatus("INACTIVE");
        when(productRepository.findByIdWithStoreAndCategory(3L)).thenReturn(Optional.of(product));

        boolean indexed = productIndexer.indexProduct(3L);

        assertFalse(indexed);
        verify(searchIndexService).delete(3L);
        verify(searchIndexService, never()).bulkUpsert(anyList());
    }

    @Test
    @DisplayName("indexProduct: store chưa APPROVED -> xoá document, KHÔNG upsert")
    void indexProduct_pendingStore_deletesDocument() {
        Product product = activeProduct(4L);
        product.getStore().setStatus("PENDING");
        when(productRepository.findByIdWithStoreAndCategory(4L)).thenReturn(Optional.of(product));

        boolean indexed = productIndexer.indexProduct(4L);

        assertFalse(indexed);
        verify(searchIndexService).delete(4L);
        verify(searchIndexService, never()).bulkUpsert(anyList());
    }

    @Test
    @DisplayName("indexProduct: sản phẩm không tồn tại -> xoá document mồ côi")
    void indexProduct_productNotFound_deletesDocument() {
        when(productRepository.findByIdWithStoreAndCategory(5L)).thenReturn(Optional.empty());

        boolean indexed = productIndexer.indexProduct(5L);

        assertFalse(indexed);
        verify(searchIndexService).delete(5L);
        verify(searchIndexService, never()).bulkUpsert(anyList());
    }

    @Test
    @DisplayName("indexProduct: productId null -> không làm gì")
    void indexProduct_nullId_isNoOp() {
        assertFalse(productIndexer.indexProduct(null));
        verify(searchIndexService, never()).delete(any());
        verify(searchIndexService, never()).bulkUpsert(anyList());
    }

    @Test
    @DisplayName("indexProducts: batch nhiều sản phẩm -> 1 lần bulk upsert (không N+1)")
    void indexProducts_batchesInSingleBulkCall() {
        Product p1 = activeProduct(10L);
        Product p2 = activeProduct(11L);
        when(productRepository.findAllByIdInWithStoreAndCategory(any()))
                .thenReturn(List.of(p1, p2));
        when(productVariantRepository.aggregatePriceAndStockByProductIds(any())).thenReturn(List.of());
        when(productReviewRepository.aggregateRatingByProductIds(any())).thenReturn(List.of());
        when(flashSaleItemRepository.findProductIdsWithActiveFlashSale(any())).thenReturn(List.of());
        when(searchIndexService.bulkUpsert(anyList())).thenReturn(2);

        int count = productIndexer.indexProducts(List.of(10L, 11L));

        assertEquals(2, count);
        ArgumentCaptor<List<SearchIndexDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(searchIndexService).bulkUpsert(captor.capture());
        assertEquals(2, captor.getValue().size());
        // Aggregate chỉ gọi 1 lần cho cả batch -> không N+1
        verify(productVariantRepository, org.mockito.Mockito.times(1))
                .aggregatePriceAndStockByProductIds(any());
        verify(productReviewRepository, org.mockito.Mockito.times(1))
                .aggregateRatingByProductIds(any());
    }

    @Test
    @DisplayName("indexProducts: danh sách rỗng -> không gọi OpenSearch")
    void indexProducts_emptyList_isNoOp() {
        assertEquals(0, productIndexer.indexProducts(List.of()));
        assertEquals(0, productIndexer.indexProducts(null));
        verify(searchIndexService, never()).bulkUpsert(anyList());
    }

    @Test
    @DisplayName("removeProduct: xoá document theo id")
    void removeProduct_deletesDocument() {
        productIndexer.removeProduct(42L);
        verify(searchIndexService).delete(42L);
    }

    @Test
    @DisplayName("removeProduct: productId null -> không gọi OpenSearch")
    void removeProduct_nullId_isNoOp() {
        productIndexer.removeProduct(null);
        verify(searchIndexService, never()).delete(any());
    }
}
