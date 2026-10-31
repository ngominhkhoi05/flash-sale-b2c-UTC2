package com.b2c.flash_sale_b2c_UTC2.search.service;

import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.product.dto.RecommendedProductResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.SearchRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.SearchResponse;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.search.client.OpenSearchException;
import com.b2c.flash_sale_b2c_UTC2.search.client.OpenSearchGateway;
import com.b2c.flash_sale_b2c_UTC2.search.client.OpenSearchProperties;
import com.b2c.flash_sale_b2c_UTC2.search.cursor.CursorCodec;
import com.b2c.flash_sale_b2c_UTC2.search.dto.SearchCursor;
import com.b2c.flash_sale_b2c_UTC2.search.dto.SearchQuerySpec;
import com.b2c.flash_sale_b2c_UTC2.search.dto.SearchResult;
import com.b2c.flash_sale_b2c_UTC2.search.repository.ProductSearchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests cho {@link SearchServiceImpl}.
 *
 * <p>Cover 3 hành vi chính của mô hình search-then-hydrate:
 * <ol>
 *   <li>OS OK -> items giữ đúng thứ tự relevance do OS quyết định.</li>
 *   <li>ID có trong OS nhưng không tồn tại trong DB -> bị loại bỏ, không để lại ô rỗng.</li>
 *   <li>OS lỗi/timeout -> fallback sang PostgreSQL với cùng shape response.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchServiceImplTest {

    @Mock
    private OpenSearchGateway openSearchGateway;

    @Mock
    private ProductSearchRepository productSearchRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ImageService imageService;

    @Mock
    private SearchFacetsService searchFacetsService;

    @Mock
    private com.b2c.flash_sale_b2c_UTC2.product.repository.CategoryRepository categoryRepository;

    private CursorCodec cursorCodec;
    private SearchServiceImpl searchService;

    @BeforeEach
    void setUp() {
        OpenSearchProperties properties = new OpenSearchProperties();
        properties.setCursorSecret("test-search-cursor-secret-value-0123456789");
        properties.getSearch().setPageSize(24);
        cursorCodec = new CursorCodec(new ObjectMapper(), properties);

        searchService = new SearchServiceImpl(
                openSearchGateway,
                cursorCodec,
                properties,
                productSearchRepository,
                productRepository,
                productVariantRepository,
                imageService,
                searchFacetsService,
                categoryRepository);

        when(productVariantRepository.aggregatePriceAndStockByProductIds(anyList()))
                .thenReturn(List.of());
        when(imageService.getPrimaryImagesByOwnerIds(any(), anyList())).thenReturn(Map.of());
        when(searchFacetsService.buildFacets(any())).thenReturn(null);
    }

    private SearchRequest request() {
        return SearchRequest.builder().q("dien thoai").build();
    }

    private RecommendedProductResponse item(long id) {
        return RecommendedProductResponse.builder().id(id).name("product-" + id).build();
    }

    @Test
    @DisplayName("OS OK: items giu dung thu tu relevance OS tra ve")
    void openSearch_preservesRelevanceOrder() {
        when(openSearchGateway.search(any())).thenReturn(new SearchResult(
                List.of(3L, 1L, 2L), "pit-1", 3, true, false, List.of()));
        when(productSearchRepository.hydrate(anyList(), any(), any()))
                .thenReturn(List.of(item(3), item(1), item(2)));

        SearchResponse response = searchService.search(request(), null);

        assertEquals(List.of(3L, 1L, 2L),
                response.getItems().stream().map(RecommendedProductResponse::getId).toList());
        assertEquals(3, response.getTotal());
        assertTrue(response.isTotalIsExact());
        assertFalse(response.isHasMore());
        assertNull(response.getNextCursor());
        assertEquals(24, response.getSize());
    }

    @Test
    @DisplayName("OS tra ID khong ton tai trong DB: ID bi loai bo, khong co item rong")
    void openSearch_dropsMissingIds() {
        when(openSearchGateway.search(any())).thenReturn(new SearchResult(
                List.of(3L, 99L, 1L), "pit-1", 3, true, false, List.of()));
        // DB chi tra ve 3 va 1 -> 99 bi loai
        when(productSearchRepository.hydrate(anyList(), any(), any()))
                .thenReturn(List.of(item(3), item(1)));

        SearchResponse response = searchService.search(request(), null);

        assertEquals(List.of(3L, 1L),
                response.getItems().stream().map(RecommendedProductResponse::getId).toList());
        assertFalse(response.isTotalIsExact(), "co ID bi loai -> total khong con chinh xac");
    }

    @Test
    @DisplayName("OS bao con trang nhung DB loai het ID: van cap nextCursor (khong ket vo han)")
    void openSearch_issuesCursorEvenWhenAllIdsDropped() {
        when(openSearchGateway.search(any())).thenReturn(new SearchResult(
                List.of(3L, 99L), "pit-1", 2, true, true, List.of(1.5, 3L)));
        when(productSearchRepository.hydrate(anyList(), any(), any())).thenReturn(List.of());

        SearchResponse response = searchService.search(request(), null);

        assertTrue(response.getItems().isEmpty());
        assertTrue(response.isHasMore());
        assertNotNull(response.getNextCursor(), "phai cap cursor de FE khong ket");
        assertFalse(response.isTotalIsExact());
    }

    @Test
    @DisplayName("OS tra du lieu: nextCursor chua PIT id moi + searchAfter")
    void openSearch_nextCursorContainsPitAndSearchAfter() {
        when(openSearchGateway.search(any())).thenReturn(new SearchResult(
                List.of(3L, 1L), "pit-new", 2, true, true, List.of(2.5, 3L)));
        when(productSearchRepository.hydrate(anyList(), any(), any()))
                .thenReturn(List.of(item(3), item(1)));

        SearchResponse response = searchService.search(request(), null);

        assertNotNull(response.getNextCursor());
        SearchCursor decoded = cursorCodec.decode(response.getNextCursor());
        assertNotNull(decoded);
        assertEquals(SearchCursor.CursorMode.OS_PIT, decoded.mode());
        assertEquals("pit-new", decoded.pitId());
        assertEquals(2, decoded.offset());
        assertEquals(List.of(2.5, 3L), decoded.searchAfter());
    }

    @Test
    @DisplayName("Cursor fingerprint lech (doi filter) -> bo qua cursor, bat dau tu trang dau")
    void cursorFingerprintMismatch_restartsFromFirstPage() {
        when(openSearchGateway.search(any())).thenReturn(new SearchResult(
                List.of(1L), "pit-1", 1, true, false, List.of()));

        // Tao cursor voi fingerprint khac
        SearchCursor foreign = cursorCodec.newCursor(
                SearchCursor.CursorMode.OS_PIT, "pit-old", List.of(1L), 48, "relevance", "fingerprint-khac");
        String token = cursorCodec.encode(foreign);

        searchService.search(request(), token);

        ArgumentCaptor<SearchQuerySpec> captor = ArgumentCaptor.forClass(SearchQuerySpec.class);
        verify(openSearchGateway).search(captor.capture());
        assertNull(captor.getValue().pitId(), "cursor khong hop le -> khong dung PIT cu");
        assertNull(captor.getValue().searchAfter());
    }

    @Test
    @DisplayName("Cursor hop le -> truyen dung PIT id + searchAfter xuong OS")
    void cursorValid_passesPitAndSearchAfter() {
        when(openSearchGateway.search(any())).thenReturn(new SearchResult(
                List.of(1L), "pit-1", 1, true, false, List.of()));

        // Dung fingerprint thuc te cua service
        String fingerprint = cursorCodec.fingerprint("dien thoai", "", "", "", "", "", "", "relevance");
        SearchCursor valid = cursorCodec.newCursor(
                SearchCursor.CursorMode.OS_PIT, "pit-old", List.of(1L, 5L), 24, "relevance", fingerprint);

        searchService.search(request(), cursorCodec.encode(valid));

        ArgumentCaptor<SearchQuerySpec> captor = ArgumentCaptor.forClass(SearchQuerySpec.class);
        verify(openSearchGateway).search(captor.capture());
        assertEquals("pit-old", captor.getValue().pitId());
        assertEquals(List.of(1L, 5L), captor.getValue().searchAfter());
    }

    @Test
    @DisplayName("OS loi/timeout -> fallback PostgreSQL tra cung shape response")
    void openSearchFailure_fallsBackToDatabase() {
        when(openSearchGateway.search(any())).thenThrow(new OpenSearchException("timeout"));
        List<Product> dbProducts = List.of(product(7L), product(8L));
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(dbProducts, Pageable.ofSize(24), 2));
        when(productSearchRepository.hydrate(anyList(), any(), any()))
                .thenReturn(List.of(item(7), item(8)));
        when(productRepository.count(any(Specification.class))).thenReturn(2L);

        SearchResponse response = searchService.search(request(), null);

        assertEquals(List.of(7L, 8L),
                response.getItems().stream().map(RecommendedProductResponse::getId).toList());
        assertEquals(2, response.getTotal());
        assertTrue(response.isTotalIsExact());
        assertEquals(24, response.getSize());
        // 2 < size -> het trang -> khong cursor
        assertFalse(response.isHasMore());
        assertNull(response.getNextCursor());
    }

    @Test
    @DisplayName("Fallback DB voi cursor offset: dung offset tu cursor")
    void fallback_usesOffsetFromCursor() {
        when(openSearchGateway.search(any())).thenThrow(new OpenSearchException("down"));

        String fingerprint = cursorCodec.fingerprint("dien thoai", "", "", "", "", "", "", "relevance");
        SearchCursor cursor = cursorCodec.newCursor(
                SearchCursor.CursorMode.DB_OFFSET, null, List.of(), 48, "relevance", fingerprint);

        List<Product> dbProducts = List.of(product(9L));
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(dbProducts, Pageable.ofSize(24), 100));
        when(productSearchRepository.hydrate(anyList(), any(), any())).thenReturn(List.of(item(9)));
        when(productRepository.count(any(Specification.class))).thenReturn(100L);

        SearchResponse response = searchService.search(request(), cursorCodec.encode(cursor));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(any(Specification.class), captor.capture());
        assertEquals(2, captor.getValue().getPageNumber(), "offset 48 / size 24 = page 2");
        assertEquals(1, response.getItems().size());
    }

    @Test
    @DisplayName("Fallback DB: cursor mode DB_OFFSET duoc cap khi con trang")
    void fallback_issuesDbOffsetCursor() {
        when(openSearchGateway.search(any())).thenThrow(new OpenSearchException("down"));
        List<Product> products = new java.util.ArrayList<>();
        for (int i = 0; i < 24; i++) {
            products.add(product(100L + i));
        }
        List<RecommendedProductResponse> hydrated = new java.util.ArrayList<>();
        for (Product p : products) {
            hydrated.add(item(p.getId()));
        }
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(products, Pageable.ofSize(24), 1000));
        when(productSearchRepository.hydrate(anyList(), any(), any()))
                .thenReturn(hydrated);
        when(productRepository.count(any(Specification.class))).thenReturn(1000L);

        SearchResponse response = searchService.search(request(), null);

        assertTrue(response.isHasMore());
        assertNotNull(response.getNextCursor());
        SearchCursor decoded = cursorCodec.decode(response.getNextCursor());
        assertNotNull(decoded);
        assertEquals(SearchCursor.CursorMode.DB_OFFSET, decoded.mode());
        assertEquals(24, decoded.offset());
    }

    @Test
    @DisplayName("Fallback DB: vuot gioi han sau 10 trang -> hasMore=false")
    void fallback_stopsAfterMaxPages() {
        when(openSearchGateway.search(any())).thenThrow(new OpenSearchException("down"));

        String fingerprint = cursorCodec.fingerprint("dien thoai", "", "", "", "", "", "", "relevance");
        SearchCursor deep = cursorCodec.newCursor(
                SearchCursor.CursorMode.DB_OFFSET, null, List.of(), 216, "relevance", fingerprint);

        List<Product> products = new java.util.ArrayList<>();
        for (int i = 0; i < 24; i++) {
            products.add(product(200L + i));
        }
        List<RecommendedProductResponse> hydrated = new java.util.ArrayList<>();
        for (Product p : products) {
            hydrated.add(item(p.getId()));
        }
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(products, Pageable.ofSize(24), 1000));
        when(productSearchRepository.hydrate(anyList(), any(), any()))
                .thenReturn(hydrated);
        when(productRepository.count(any(Specification.class))).thenReturn(1000L);

        SearchResponse response = searchService.search(request(), cursorCodec.encode(deep));

        assertFalse(response.isHasMore(), "offset 216 + 24 >= 240 -> dung fallback");
        assertNull(response.getNextCursor());
    }

    @Test
    @DisplayName("Hydrate goi 1 lan cho toan bo id (khong N+1)")
    void hydrate_singleBatchCall() {
        when(openSearchGateway.search(any())).thenReturn(new SearchResult(
                List.of(1L, 2L, 3L), "pit-1", 3, true, false, List.of()));
        when(productSearchRepository.hydrate(anyList(), any(), any()))
                .thenReturn(List.of(item(1), item(2), item(3)));

        searchService.search(request(), null);

        verify(productSearchRepository, org.mockito.Mockito.times(1)).hydrate(anyList(), any(), any());
    }

    @Test
    @DisplayName("OS duong dan chinh: khong truy van repository DB cho danh sach san pham")
    void openSearchBranch_doesNotQueryProductRepositoryForList() {
        when(openSearchGateway.search(any())).thenReturn(new SearchResult(
                List.of(1L), "pit-1", 1, true, false, List.of()));
        when(productSearchRepository.hydrate(anyList(), any(), any())).thenReturn(List.of(item(1)));

        searchService.search(request(), null);

        verify(productRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    /**
     * Tạo mock Product. Dùng {@code doReturn} (không phải {@code when}) vì thường
     * được truyền làm tham số cho {@code thenReturn(...)} — nếu dùng {@code when}
     * sẽ gây UnfinishedStubbingException do nested stubbing.
     */
    private Product product(long id) {
        Product product = mock(Product.class);
        org.mockito.Mockito.doReturn(id).when(product).getId();
        org.mockito.Mockito.doReturn("product-" + id).when(product).getName();
        org.mockito.Mockito.doReturn(0).when(product).getTotalSold();
        return product;
    }

    @Test
    @DisplayName("Price range + anh duoc gan vao item khi hydrate")
    void hydrate_appliesPriceRangeAndImage() {
        when(openSearchGateway.search(any())).thenReturn(new SearchResult(
                List.of(1L), "pit-1", 1, true, false, List.of()));
        when(productVariantRepository.aggregatePriceAndStockByProductIds(anyList()))
                .thenReturn(List.<Object[]>of(new Object[]{1L, new BigDecimal("1000"), new BigDecimal("2000"), 7}));
        when(productSearchRepository.hydrate(anyList(), any(), any())).thenReturn(List.of(item(1)));

        searchService.search(request(), null);

        ArgumentCaptor<Map<Long, ProductSearchRepository.PriceRange>> captor = ArgumentCaptor.forClass(Map.class);
        verify(productSearchRepository).hydrate(anyList(), captor.capture(), any());
        ProductSearchRepository.PriceRange range = captor.getValue().get(1L);
        assertNotNull(range);
        assertEquals(new BigDecimal("1000"), range.minPrice());
        assertEquals(new BigDecimal("2000"), range.maxPrice());
        assertEquals(7, range.totalStock());
    }
}
