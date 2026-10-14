package com.b2c.flash_sale_b2c_UTC2.product.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.image.dto.ImageResponse;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductSearchRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductSearchResponse;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Category;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.product.exception.ProductErrorCode;
import com.b2c.flash_sale_b2c_UTC2.product.repository.CategoryRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.review.repository.ProductReviewRepository;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.enums.StoreTier;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import com.b2c.flash_sale_b2c_UTC2.voucher.entity.Voucher;
import com.b2c.flash_sale_b2c_UTC2.voucher.repository.VoucherRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductSearchServiceTest {

    @Mock private EntityManager entityManager;
    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductReviewRepository productReviewRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private VoucherRepository voucherRepository;
    @Mock private ImageService imageService;

    @Mock private CriteriaBuilder cb;
    @Mock private CriteriaQuery<Long> countQuery;
    @Mock private Root<Product> countRoot;
    @Mock private TypedQuery<Long> typedCountQuery;

    @Mock private CriteriaQuery<Product> selectQuery;
    @Mock private Root<Product> selectRoot;
    @Mock private TypedQuery<Product> typedSelectQuery;
    @Mock private Path<Object> statusPath;
    @Mock private Path<Object> storePath;
    @Mock private Path<Object> storeStatusPath;
    @Mock private Predicate dummyPredicate;

    @InjectMocks
    private ProductSearchServiceImpl productSearchService;

    @BeforeEach
    void setUp() {
        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(Long.class)).thenReturn(countQuery);
        when(cb.createQuery(Product.class)).thenReturn(selectQuery);
        when(countQuery.from(Product.class)).thenReturn(countRoot);
        when(selectQuery.from(Product.class)).thenReturn(selectRoot);

        when(countRoot.get("status")).thenReturn(statusPath);
        when(countRoot.get("store")).thenReturn(storePath);
        when(storePath.get("status")).thenReturn(storeStatusPath);

        when(selectRoot.get("status")).thenReturn(statusPath);
        when(selectRoot.get("store")).thenReturn(storePath);

        when(cb.equal(any(), any())).thenReturn(dummyPredicate);
        when(cb.like(any(), anyString(), anyChar())).thenReturn(dummyPredicate);
        when(cb.lower(any())).thenReturn(mock(Expression.class));
        when(cb.countDistinct(any())).thenReturn(mock(Expression.class));

        when(countQuery.select(any())).thenReturn(countQuery);
        when(countQuery.where(any(Predicate[].class))).thenReturn(countQuery);
        when(entityManager.createQuery(countQuery)).thenReturn(typedCountQuery);

        when(selectQuery.where(any(Predicate[].class))).thenReturn(selectQuery);
        when(selectQuery.distinct(true)).thenReturn(selectQuery);
        when(entityManager.createQuery(selectQuery)).thenReturn(typedSelectQuery);
        when(typedSelectQuery.setFirstResult(anyInt())).thenReturn(typedSelectQuery);
        when(typedSelectQuery.setMaxResults(anyInt())).thenReturn(typedSelectQuery);
    }

    @Test
    @DisplayName("Ném PAGE_SIZE_EXCEEDED khi pageSize vượt quá 50")
    void searchProducts_pageSizeExceeded_throwsException() {
        ProductSearchRequest request = ProductSearchRequest.builder()
                .q("laptop")
                .pageSize(51)
                .build();

        assertThatThrownBy(() -> productSearchService.searchProducts(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.PAGE_SIZE_EXCEEDED);
    }

    @Test
    @DisplayName("Ném INVALID_SORT_OPTION khi sort không nằm trong whitelist")
    void searchProducts_invalidSort_throwsException() {
        ProductSearchRequest request = ProductSearchRequest.builder()
                .q("laptop")
                .sort("unsupported_sort")
                .build();

        assertThatThrownBy(() -> productSearchService.searchProducts(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INVALID_SORT_OPTION);
    }

    @Test
    @DisplayName("Ném INVALID_SEARCH_PARAMS khi minPrice > maxPrice")
    void searchProducts_minPriceGreaterThanMaxPrice_throwsException() {
        ProductSearchRequest request = ProductSearchRequest.builder()
                .minPrice(new BigDecimal("500000"))
                .maxPrice(new BigDecimal("100000"))
                .build();

        assertThatThrownBy(() -> productSearchService.searchProducts(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INVALID_SEARCH_PARAMS);
    }

    @Test
    @DisplayName("Ném INVALID_SEARCH_PARAMS khi minRating ngoài 1..5")
    void searchProducts_invalidMinRating_throwsException() {
        ProductSearchRequest request = ProductSearchRequest.builder()
                .minRating(6)
                .build();

        assertThatThrownBy(() -> productSearchService.searchProducts(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INVALID_SEARCH_PARAMS);
    }

    @Test
    @DisplayName("Trả về danh sách rỗng và empty facets khi không tìm thấy kết quả nào")
    void searchProducts_noResults_returnsEmptyResponse() {
        when(typedCountQuery.getSingleResult()).thenReturn(0L);

        ProductSearchRequest request = ProductSearchRequest.builder()
                .q("not_found_query_12345")
                .page(1)
                .pageSize(20)
                .build();

        ProductSearchResponse response = productSearchService.searchProducts(request);

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotal()).isEqualTo(0L);
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(20);
        assertThat(response.getFacets().getCategories()).isEmpty();
        assertThat(response.getMall()).isNull();
    }

    @Test
    @DisplayName("Tìm kiếm thành công với facets và mall store")
    void searchProducts_success_returnsItemsAndFacets() {
        when(typedCountQuery.getSingleResult()).thenReturn(1L);

        Store mallStore = Store.builder()
                .id(10L)
                .storeName("Apple Official Store")
                .status("APPROVED")
                .tier(StoreTier.OFFICIAL_MALL)
                .build();

        Category category = Category.builder()
                .id(1)
                .name("Điện thoại & Phụ kiện")
                .slug("dien-thoai-phu-kien")
                .build();

        Product product = Product.builder()
                .id(100L)
                .name("iPhone 15 Pro Max")
                .slug("iphone-15-pro-max")
                .status("ACTIVE")
                .store(mallStore)
                .category(category)
                .build();

        when(typedSelectQuery.getResultList()).thenReturn(List.of(product));

        ProductVariant variant = ProductVariant.builder()
                .id(200L)
                .product(product)
                .sku("IP15-256")
                .status("ACTIVE")
                .originalPrice(new BigDecimal("30000000"))
                .stockQuantity(50)
                .build();

        when(productVariantRepository.findByProductIdInAndStatus(List.of(100L), "ACTIVE"))
                .thenReturn(List.of(variant));

        ImageResponse imageResponse = ImageResponse.builder()
                .id(999L)
                .url("https://res.cloudinary.com/demo/image/upload/v1/iphone15.jpg")
                .build();

        when(imageService.getPrimaryImagesByOwnerIds(ImageOwnerType.PRODUCT, List.of(100L)))
                .thenReturn(Map.of(100L, imageResponse));

        when(productReviewRepository.findAvgRatingByProductIdIn(List.of(100L)))
                .thenReturn(List.<Object[]>of(new Object[]{100L, 4.8}));

        @SuppressWarnings("unchecked")
        TypedQuery<Store> mallQuery = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(Store.class))).thenReturn(mallQuery);
        when(mallQuery.setParameter(anyString(), any())).thenReturn(mallQuery);
        when(mallQuery.setMaxResults(anyInt())).thenReturn(mallQuery);
        when(mallQuery.getResultList()).thenReturn(List.of(mallStore));

        ProductSearchRequest request = ProductSearchRequest.builder()
                .q("Apple")
                .page(1)
                .pageSize(20)
                .sort("relevance")
                .build();

        ProductSearchResponse response = productSearchService.searchProducts(request);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getTotal()).isEqualTo(1L);
        assertThat(response.getItems().get(0).getName()).isEqualTo("iPhone 15 Pro Max");
        assertThat(response.getItems().get(0).getPrice()).isEqualByComparingTo(new BigDecimal("30000000"));
        assertThat(response.getItems().get(0).getRating()).isEqualTo(4.8);
        assertThat(response.getItems().get(0).getStoreBadge()).isEqualTo("OfficialMall");
        assertThat(response.getMall()).isNotNull();
        assertThat(response.getMall().getTitle()).isEqualTo("Apple Official Store Official Mall");
    }
}
