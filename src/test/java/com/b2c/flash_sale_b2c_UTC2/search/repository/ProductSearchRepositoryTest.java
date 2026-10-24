package com.b2c.flash_sale_b2c_UTC2.search.repository;

import com.b2c.flash_sale_b2c_UTC2.product.dto.RecommendedProductResponse;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests cho {@link ProductSearchRepository} — lớp "defense layer 2" của
 * mô hình search-then-hydrate.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductSearchRepositoryTest {

    @Mock
    private ProductRepository productRepository;

    private ProductSearchRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ProductSearchRepository(productRepository);
    }

    /**
     * Tạo mock Product. Phải gọi {@code doReturn} (không phải {@code when}) vì
     * thường được dùng làm tham số cho {@code thenReturn(...)} — nếu dùng
     * {@code when} sẽ gây UnfinishedStubbingException do nested stubbing.
     */
    private Product product(long id) {
        Product product = mock(Product.class);
        org.mockito.Mockito.doReturn(id).when(product).getId();
        org.mockito.Mockito.doReturn("product-" + id).when(product).getName();
        org.mockito.Mockito.doReturn(5).when(product).getTotalSold();
        return product;
    }

    @Test
    @DisplayName("loadByIdsInOrder: goi DUY NHAT 1 query cho toan bo id (khong N+1)")
    void loadByIdsInOrder_singleQuery() {
        List<Product> products = List.of(product(1), product(2), product(3));
        when(productRepository.findAllActiveByIdInWithStoreAndCategory(any()))
                .thenReturn(products);

        repository.loadByIdsInOrder(List.of(1L, 2L, 3L));

        verify(productRepository, times(1)).findAllActiveByIdInWithStoreAndCategory(any());
    }

    @Test
    @DisplayName("loadByIdsInOrder: danh sach rong -> khong truy van DB")
    void loadByIdsInOrder_emptyIds_noQuery() {
        assertTrue(repository.loadByIdsInOrder(List.of()).isEmpty());
        assertTrue(repository.loadByIdsInOrder(null).isEmpty());
        verify(productRepository, times(0)).findAllActiveByIdInWithStoreAndCategory(any());
    }

    @Test
    @DisplayName("loadOrderedProducts: giu thu tu input, bo id khong ton tai")
    void loadOrderedProducts_preservesOrderDropsMissing() {
        List<Product> products = List.of(product(1), product(2));
        when(productRepository.findAllActiveByIdInWithStoreAndCategory(any()))
                .thenReturn(products);

        List<Product> ordered = repository.loadOrderedProducts(List.of(3L, 1L, 2L));

        assertEquals(List.of(1L, 2L), ordered.stream().map(Product::getId).toList());
    }

    @Test
    @DisplayName("hydrate: giu thu tu relevance cua OS")
    void hydrate_preservesRelevanceOrder() {
        List<Product> products = List.of(product(3), product(1), product(2));
        when(productRepository.findAllActiveByIdInWithStoreAndCategory(any()))
                .thenReturn(products);

        List<RecommendedProductResponse> items = repository.hydrate(
                List.of(3L, 1L, 2L), Map.of(), Map.of());

        assertEquals(List.of(3L, 1L, 2L),
                items.stream().map(RecommendedProductResponse::getId).toList());
    }

    @Test
    @DisplayName("hydrate: id khong ton tai trong DB bi loai bo (khong con item rong)")
    void hydrate_dropsMissingIds() {
        List<Product> products = List.of(product(3), product(1));
        when(productRepository.findAllActiveByIdInWithStoreAndCategory(any()))
                .thenReturn(products);

        List<RecommendedProductResponse> items = repository.hydrate(
                List.of(3L, 99L, 1L), Map.of(), Map.of());

        assertEquals(2, items.size());
        assertEquals(List.of(3L, 1L),
                items.stream().map(RecommendedProductResponse::getId).toList());
        assertTrue(items.stream().noneMatch(i -> i.getId() == null));
    }

    @Test
    @DisplayName("hydrate: gan price range + image + stock vao item")
    void hydrate_mapsPriceRangeAndImage() {
        List<Product> products = List.of(product(1));
        when(productRepository.findAllActiveByIdInWithStoreAndCategory(any()))
                .thenReturn(products);

        Map<Long, ProductSearchRepository.PriceRange> ranges = Map.of(
                1L, new ProductSearchRepository.PriceRange(
                        new BigDecimal("1000"), new BigDecimal("2500"), 12));
        Map<Long, String> images = Map.of(1L, "https://cdn/img.png");

        List<RecommendedProductResponse> items = repository.hydrate(List.of(1L), ranges, images);

        RecommendedProductResponse item = items.get(0);
        assertEquals(new BigDecimal("1000"), item.getMinPrice());
        assertEquals(new BigDecimal("2500"), item.getMaxPrice());
        assertEquals(12, item.getTotalStock());
        assertEquals("https://cdn/img.png", item.getImageUrl());
        assertEquals("product-1", item.getName());
        assertEquals(5, item.getTotalSold());
        assertNull(item.getLabel());
    }

    @Test
    @DisplayName("hydrate: san pham khong co variant -> price null, stock 0")
    void hydrate_missingPriceRange_defaults() {
        List<Product> products = List.of(product(1));
        when(productRepository.findAllActiveByIdInWithStoreAndCategory(any()))
                .thenReturn(products);

        List<RecommendedProductResponse> items = repository.hydrate(List.of(1L), Map.of(), Map.of());

        RecommendedProductResponse item = items.get(0);
        assertNull(item.getMinPrice());
        assertNull(item.getMaxPrice());
        assertEquals(0, item.getTotalStock());
        assertNull(item.getImageUrl());
    }

    @Test
    @DisplayName("hydrate: danh sach rong -> tra list rong")
    void hydrate_emptyIds() {
        assertTrue(repository.hydrate(List.of(), Map.of(), Map.of()).isEmpty());
        assertTrue(repository.hydrate(null, Map.of(), Map.of()).isEmpty());
        verify(productRepository, times(0)).findAllActiveByIdInWithStoreAndCategory(any());
    }

    @Test
    @DisplayName("PriceRange: giu nguyen gia tri")
    void priceRange_holdsValues() {
        ProductSearchRepository.PriceRange range = new ProductSearchRepository.PriceRange(
                new BigDecimal("1.00"), new BigDecimal("2.00"), 3);
        assertNotNull(range);
        assertEquals(new BigDecimal("1.00"), range.minPrice());
        assertEquals(new BigDecimal("2.00"), range.maxPrice());
        assertEquals(3, range.totalStock());
    }
}
