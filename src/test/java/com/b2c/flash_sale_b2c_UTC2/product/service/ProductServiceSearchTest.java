package com.b2c.flash_sale_b2c_UTC2.product.service;

import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderItemRepository;
import com.b2c.flash_sale_b2c_UTC2.product.dto.FacetItemDto;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductSummaryResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.SearchFacetsDto;
import com.b2c.flash_sale_b2c_UTC2.product.dto.SearchRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.SearchResponse;
import com.b2c.flash_sale_b2c_UTC2.product.enums.SearchService;
import com.b2c.flash_sale_b2c_UTC2.product.enums.SearchSortKey;
import com.b2c.flash_sale_b2c_UTC2.product.mapper.ProductVariantMapper;
import com.b2c.flash_sale_b2c_UTC2.product.repository.CategoryRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.review.repository.ProductReviewRepository;
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
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductServiceImpl#searchProducts(SearchRequest, Pageable)}.
 *
 * The test target is intentionally written BEFORE the service method exists.
 * It currently fails to compile (RED). Once {@code searchProducts} is
 * implemented, the tests should pass (GREEN).
 *
 * Note: for compilation purposes, this test references {@link SearchRequest}
 * (which will live next to the service method). The reference to
 * {@link com.b2c.flash_sale_b2c_UTC2.product.enums.SearchCity} uses a
 * dedicated inline map. {@code SearchService} is the only hard enum
 * dependency required for service flag handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceSearchTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private FlashSaleItemRepository flashSaleItemRepository;
    @Mock private ProductReviewRepository productReviewRepository;
    @Mock private ImageService imageService;
    @Spy private ProductVariantMapper productVariantMapper = mock(ProductVariantMapper.class);

    @InjectMocks private ProductServiceImpl productService;

    @Test
    @DisplayName("searchProducts: no filters -> returns items + facets + total")
    void searchProducts_noFilters_returnsItemsAndFacets() {
        // Arrange
        when(productRepository.findAll(
                any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty(Pageable.ofSize(20)));
        when(productVariantRepository.findByProductIdInAndStatus(any(), any()))
                .thenReturn(List.of());
        when(imageService.getPrimaryImagesByOwnerIds(any(), any()))
                .thenReturn(Map.of());
        when(productRepository.count(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(0L);

        // Act
        SearchRequest req = new SearchRequest(
                "",          // q
                null,        // subcategories
                null,        // cities
                null,        // services
                null, null,  // priceMin/Max
                null,        // rating
                SearchSortKey.RELEVANCE
        );
        SearchResponse response = productService.searchProducts(req, Pageable.ofSize(20));

        // Assert
        assertNotNull(response);
        assertNotNull(response.getItems());
        assertNotNull(response.getFacets());
        assertEquals(0, response.getPage());
        assertEquals(20, response.getPageSize());
    }

    @Test
    @DisplayName("searchProducts: facets always carry 4 hard-coded subcategories")
    void searchProducts_facetsContainFiveSubcategoryKeys() {
        when(productRepository.findAll(
                any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty(Pageable.ofSize(20)));
        when(productVariantRepository.findByProductIdInAndStatus(any(), any()))
                .thenReturn(List.of());
        when(imageService.getPrimaryImagesByOwnerIds(any(), any()))
                .thenReturn(Map.of());
        when(productRepository.count(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(0L);

        SearchResponse response = productService.searchProducts(
                new SearchRequest("", null, null, null, null, null, null, SearchSortKey.NEWEST),
                Pageable.ofSize(20)
        );

        SearchFacetsDto facets = response.getFacets();
        assertNotNull(facets);
        assertEquals(5, facets.getSubcategories().size());
        // Keys are the canonical UI values
        assertEquals(true,
                facets.getSubcategories().stream().map(FacetItemDto::getKey).toList()
                        .containsAll(List.of("true-wireless", "over-ear", "sport-waterproof", "gaming-mic", "accessories"))
        );
    }

    @Test
    @DisplayName("searchProducts: facets always carry 4 hard-coded cities")
    void searchProducts_facetsContainFourCities() {
        when(productRepository.findAll(
                any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty(Pageable.ofSize(20)));
        when(productVariantRepository.findByProductIdInAndStatus(any(), any()))
                .thenReturn(List.of());
        when(imageService.getPrimaryImagesByOwnerIds(any(), any()))
                .thenReturn(Map.of());
        when(productRepository.count(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(0L);

        SearchResponse response = productService.searchProducts(
                new SearchRequest("", null, null, null, null, null, null, SearchSortKey.NEWEST),
                Pageable.ofSize(20)
        );

        assertEquals(4, response.getFacets().getCities().size());
    }

    @Test
    @DisplayName("searchProducts: facets always carry 4 hard-coded services")
    void searchProducts_facetsContainFourServices() {
        when(productRepository.findAll(
                any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty(Pageable.ofSize(20)));
        when(productVariantRepository.findByProductIdInAndStatus(any(), any()))
                .thenReturn(List.of());
        when(imageService.getPrimaryImagesByOwnerIds(any(), any()))
                .thenReturn(Map.of());
        when(productRepository.count(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(0L);

        SearchResponse response = productService.searchProducts(
                new SearchRequest("", null, null, null, null, null, null, SearchSortKey.NEWEST),
                Pageable.ofSize(20)
        );

        assertEquals(4, response.getFacets().getServices().size());
        List<String> serviceKeys = response.getFacets().getServices().stream()
                .map(FacetItemDto::getKey)
                .toList();
        for (SearchService s : SearchService.values()) {
            assertEquals(true, serviceKeys.contains(s.getKey()),
                    "Missing service facet key: " + s.getKey());
        }
    }

    @Test
    @DisplayName("searchProducts: response uses 0-based page from Pageable")
    void searchProducts_responseUsesZeroBasedPage() {
        when(productRepository.findAll(
                any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty(Pageable.ofSize(20).withPage(2)));
        when(productVariantRepository.findByProductIdInAndStatus(any(), any()))
                .thenReturn(List.of());
        when(imageService.getPrimaryImagesByOwnerIds(any(), any()))
                .thenReturn(Map.of());
        when(productRepository.count(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(0L);

        SearchResponse response = productService.searchProducts(
                new SearchRequest("", null, null, null, null, null, null, SearchSortKey.NEWEST),
                Pageable.ofSize(20).withPage(2)  // FE 1-based page=3 -> BE 0-based page=2
        );

        assertEquals(2, response.getPage());
    }
}
