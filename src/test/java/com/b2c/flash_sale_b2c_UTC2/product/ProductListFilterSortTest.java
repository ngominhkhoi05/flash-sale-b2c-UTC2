package com.b2c.flash_sale_b2c_UTC2.product;

import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductFilterRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductSummaryResponse;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.product.enums.ProductSortOption;
import com.b2c.flash_sale_b2c_UTC2.product.repository.CategoryRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderItemRepository;
import com.b2c.flash_sale_b2c_UTC2.review.repository.ProductReviewRepository;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductListFilterSortTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private FlashSaleItemRepository flashSaleItemRepository;
    @Mock private ProductReviewRepository productReviewRepository;
    @Mock private ImageService imageService;
    @Spy private com.b2c.flash_sale_b2c_UTC2.product.mapper.ProductVariantMapper productVariantMapper =
            mock(com.b2c.flash_sale_b2c_UTC2.product.mapper.ProductVariantMapper.class);

    @InjectMocks private com.b2c.flash_sale_b2c_UTC2.product.service.ProductServiceImpl productService;

    private Store mkStore(Long id) {
        return Store.builder().id(id).storeName("Shop " + id).status("APPROVED").build();
    }

    private Category mkCat(Integer id) {
        return Category.builder().id(id).name("Cat " + id).build();
    }

    private Product mkProd(Long id, Store s, Category c, Instant t, Integer sold) {
        return Product.builder().id(id).store(s).category(c).name("P" + id)
                .status("ACTIVE").createdAt(t).totalSold(sold != null ? sold : 0).build();
    }

    private Page<Product> mkPage(List<Product> items, int pg, int sz) {
        return new PageImpl(items, PageRequest.of(pg, sz), items.size());
    }

    private PageResponse<ProductSummaryResponse> runSvc(ProductFilterRequest f, Pageable p) {
        when(productVariantRepository.findByProductIdInAndStatus(any(), eq("ACTIVE"))).thenReturn(List.of());
        when(imageService.getPrimaryImagesByOwnerIds(any(), any())).thenReturn(Map.of());
        return productService.getPublicProducts(f, p);
    }

    @Test
    @DisplayName("sort=SOLD_DESC: verifies repository called with spec+pageable")
    void sortBySoldDesc() {
        Store s = mkStore(1L);
        Category c = mkCat(1);
        Instant n = Instant.now();
        Product p1 = mkProd(1L, s, c, n, 100);
        Product p2 = mkProd(2L, s, c, n, 50);
        Product p3 = mkProd(3L, s, c, n, 200);
        List<Product> sorted = List.of(p3, p1, p2);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mkPage(sorted, 0, 20));
        ProductFilterRequest f = new ProductFilterRequest();
        f.setSortOption(ProductSortOption.SOLD_DESC);
        PageResponse<ProductSummaryResponse> r = runSvc(f, PageRequest.of(0, 20));
        assertEquals(3, r.getItems().size());
        verify(productRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("sort=PRICE_ASC: uses findAllOrderByMinPriceAsc method")
    void sortByPriceAsc() {
        Store s = mkStore(1L);
        Category c = mkCat(1);
        Instant n = Instant.now();
        Product p1 = mkProd(1L, s, c, n, 10);
        Product p2 = mkProd(2L, s, c, n, 10);
        Product p3 = mkProd(3L, s, c, n, 10);
        List<Product> sorted = List.of(p3, p1, p2);
        when(productRepository.findAllOrderByMinPriceAsc(any())).thenReturn(sorted);
        ProductVariant v3 = ProductVariant.builder()
                .id(3L).product(p3).originalPrice(BigDecimal.valueOf(30)).status("ACTIVE").build();
        ProductVariant v1 = ProductVariant.builder()
                .id(1L).product(p1).originalPrice(BigDecimal.valueOf(50)).status("ACTIVE").build();
        ProductVariant v2 = ProductVariant.builder()
                .id(2L).product(p2).originalPrice(BigDecimal.valueOf(100)).status("ACTIVE").build();
        when(productVariantRepository.findByProductIdInAndStatus(any(), eq("ACTIVE")))
                .thenReturn(List.of(v3, v1, v2));
        when(imageService.getPrimaryImagesByOwnerIds(any(), any())).thenReturn(Map.of());
        ProductFilterRequest f = new ProductFilterRequest();
        f.setSortOption(ProductSortOption.PRICE_ASC);
        PageResponse<ProductSummaryResponse> r = productService.getPublicProducts(f, PageRequest.of(0, 20));
        assertEquals(3, r.getItems().size());
        assertEquals(3L, r.getItems().get(0).getId());
        assertEquals(1L, r.getItems().get(1).getId());
        assertEquals(2L, r.getItems().get(2).getId());
    }

    @Test
    @DisplayName("sort=RATING_DESC: repository called with spec")
    void sortByRatingDesc() {
        Store s = mkStore(1L);
        Category c = mkCat(1);
        Instant n = Instant.now();
        Product p1 = mkProd(1L, s, c, n, 10);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mkPage(List.of(p1), 0, 20));
        ProductFilterRequest f = new ProductFilterRequest();
        f.setSortOption(ProductSortOption.RATING_DESC);
        PageResponse<ProductSummaryResponse> r = runSvc(f, PageRequest.of(0, 20));
        assertEquals(1, r.getItems().size());
        verify(productRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("filter rating=4: only filtered products returned")
    void filterByRating() {
        Store s = mkStore(1L);
        Category c = mkCat(1);
        Instant n = Instant.now();
        Product p1 = mkProd(1L, s, c, n, 10);
        Product p2 = mkProd(2L, s, c, n, 10);
        Product p3 = mkProd(3L, s, c, n, 10);
        List<Product> filtered = List.of(p2, p3);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mkPage(filtered, 0, 20));
        ProductFilterRequest f = new ProductFilterRequest();
        f.setRating(4);
        PageResponse<ProductSummaryResponse> r = runSvc(f, PageRequest.of(0, 20));
        assertEquals(2, r.getItems().size());
        assertFalse(r.getItems().stream().anyMatch(p -> p.getId().equals(1L)));
    }

    @Test
    @DisplayName("filter minSold=50: only high-sold products returned")
    void filterByMinSold() {
        Store s = mkStore(1L);
        Category c = mkCat(1);
        Instant n = Instant.now();
        Product p1 = mkProd(1L, s, c, n, 30);
        Product p2 = mkProd(2L, s, c, n, 100);
        Product p3 = mkProd(3L, s, c, n, 200);
        List<Product> filtered = List.of(p2, p3);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mkPage(filtered, 0, 20));
        ProductFilterRequest f = new ProductFilterRequest();
        f.setMinSold(50);
        PageResponse<ProductSummaryResponse> r = runSvc(f, PageRequest.of(0, 20));
        assertEquals(2, r.getItems().size());
        assertFalse(r.getItems().stream().anyMatch(p -> p.getId().equals(1L)));
    }

    @Test
    @DisplayName("filter flashSaleOnly=true: flash-sale products included")
    void filterByFlashSaleOnly() {
        Store s = mkStore(1L);
        Category c = mkCat(1);
        Instant n = Instant.now();
        Product p1 = mkProd(1L, s, c, n, 10);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mkPage(List.of(p1), 0, 20));
        ProductFilterRequest f = new ProductFilterRequest();
        f.setFlashSaleOnly(true);
        PageResponse<ProductSummaryResponse> r = runSvc(f, PageRequest.of(0, 20));
        assertEquals(1, r.getItems().size());
        verify(productRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("filter hasDiscount=true: discounted products included")
    void filterByHasDiscount() {
        Store s = mkStore(1L);
        Category c = mkCat(1);
        Instant n = Instant.now();
        Product p1 = mkProd(1L, s, c, n, 10);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mkPage(List.of(p1), 0, 20));
        ProductFilterRequest f = new ProductFilterRequest();
        f.setHasDiscount(true);
        PageResponse<ProductSummaryResponse> r = runSvc(f, PageRequest.of(0, 20));
        assertEquals(1, r.getItems().size());
        verify(productRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("all params null: default behavior unchanged")
    void allParamsNull() {
        Store s = mkStore(1L);
        Category c = mkCat(1);
        Instant n = Instant.now();
        Product p1 = mkProd(1L, s, c, n, 10);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mkPage(List.of(p1), 0, 20));
        PageResponse<ProductSummaryResponse> r = runSvc(new ProductFilterRequest(), PageRequest.of(0, 20));
        assertEquals(1, r.getItems().size());
    }
}