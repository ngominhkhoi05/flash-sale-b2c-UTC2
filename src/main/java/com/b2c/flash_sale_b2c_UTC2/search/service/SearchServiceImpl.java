package com.b2c.flash_sale_b2c_UTC2.search.service;

import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductFilterRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.RecommendedProductResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.SearchRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.SearchResponse;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.product.spec.ProductSpecifications;
import com.b2c.flash_sale_b2c_UTC2.search.client.OpenSearchGateway;
import com.b2c.flash_sale_b2c_UTC2.search.client.OpenSearchProperties;
import com.b2c.flash_sale_b2c_UTC2.search.cursor.CursorCodec;
import com.b2c.flash_sale_b2c_UTC2.search.dto.SearchCursor;
import com.b2c.flash_sale_b2c_UTC2.search.dto.SearchQuerySpec;
import com.b2c.flash_sale_b2c_UTC2.search.dto.SearchResult;
import com.b2c.flash_sale_b2c_UTC2.search.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrate tìm kiếm: OpenSearch (chính) → fallback PostgreSQL.
 *
 * <p>Luồng chính (search-then-hydrate):
 * <ol>
 *   <li>Decode cursor → xác thực HMAC + fingerprint (nếu lệch query → bỏ cursor).</li>
 *   <li>OpenSearch trả về <b>product id theo thứ tự relevance</b> ({@code _source: false}).</li>
 *   <li>Hydrate từ PostgreSQL bằng 1 truy vấn {@code IN (...)} — giữ nguyên thứ tự.</li>
 *   <li>Id không hydrate được bị loại bỏ (sản phẩm bị ẩn/xoá giữa chừng).</li>
 *   <li>Mã hoá cursor kế tiếp (chứa PIT id mới + sort values) bằng HMAC.</li>
 * </ol>
 *
 * <p>Fallback: khi OpenSearch lỗi/timeout → truy vấn PostgreSQL với offset lấy từ
 * cursor, giới hạn sâu tối đa {@value #MAX_FALLBACK_PAGES} trang.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    /** Số trang tối đa trong nhánh fallback DB (240 item với size = 24). */
    public static final int MAX_FALLBACK_PAGES = 10;

    /** Kích thước trang mặc định khi client không chỉ định. */
    public static final int DEFAULT_PAGE_SIZE = 24;

    private final OpenSearchGateway openSearchGateway;
    private final CursorCodec cursorCodec;
    private final OpenSearchProperties properties;
    private final ProductSearchRepository productSearchRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ImageService imageService;
    private final SearchFacetsService searchFacetsService;

    @Override
    public SearchResponse search(SearchRequest request, String cursorToken) {
        int size = properties.getSearch().getPageSize() > 0
                ? properties.getSearch().getPageSize()
                : DEFAULT_PAGE_SIZE;
        String sortKey = request.getSort() == null ? "relevance" : request.getSort().getValue();
        String fingerprint = buildFingerprint(request, sortKey);

        SearchCursor cursor = cursorCodec.decode(cursorToken);
        if (cursor != null && !fingerprint.equals(cursor.fingerprint())) {
            // Client đổi filter/sort giữa chừng -> cursor cũ không còn hợp lệ.
            log.info("[SearchServiceImpl] cursor fingerprint mismatch - restarting from first page");
            cursor = null;
        }
        if (cursor != null && !sortKey.equals(cursor.sortKey())) {
            log.info("[SearchServiceImpl] cursor sort mismatch - restarting from first page");
            cursor = null;
        }

        try {
            return searchViaOpenSearch(request, cursor, size, sortKey, fingerprint);
        } catch (Exception e) {
            log.warn("[SearchServiceImpl] OpenSearch unavailable, falling back to PostgreSQL: {}",
                    e.getMessage());
            return searchViaDatabase(request, cursor, size, sortKey, fingerprint);
        }
    }

    // ────────────────────────── OpenSearch branch ──────────────────────────

    private SearchResponse searchViaOpenSearch(SearchRequest request,
                                               SearchCursor cursor,
                                               int size,
                                               String sortKey,
                                               String fingerprint) {
        SearchQuerySpec spec = new SearchQuerySpec(
                normalizeKeyword(request.getQ()),
                List.of(),
                request.getPriceMin(),
                request.getPriceMax(),
                request.getRating(),
                isFlashSaleOnly(request),
                sortKey,
                size,
                cursor != null ? cursor.pitId() : null,
                cursor != null ? cursor.searchAfter() : null);

        SearchResult result = openSearchGateway.search(spec);
        List<Long> ids = result.productIds();

        List<RecommendedProductResponse> items = hydrate(ids);

        // Id bị DB loại bỏ -> total không còn chính xác.
        boolean dropped = items.size() != ids.size();
        boolean totalIsExact = result.totalIsExact() && !dropped;

        // hasMore = OS còn trang VÀ (DB không lọc bỏ hết) — nếu DB lọc bỏ hết vẫn
        // phải cấp cursor để FE không bị kẹt.
        boolean hasMore = result.hasMore();

        String nextCursor = null;
        if (hasMore && !result.lastSortValues().isEmpty()) {
            // offset = so item THUC SU da tra ve cho client (khong phai so hit cua OS).
            // Neu OS tra hit bi DB loai bo, client thay it it hon -> offset phai phan
            // anh so item da nhan de nhanh fallback DB dung cho (khong bo qua san pham).
            SearchCursor payload = cursorCodec.newCursor(
                    SearchCursor.CursorMode.OS_PIT,
                    result.pitId(),
                    result.lastSortValues(),
                    items.size() + (cursor != null ? cursor.offset() : 0),
                    sortKey,
                    fingerprint);
            nextCursor = cursorCodec.encode(payload);
        }

        return SearchResponse.builder()
                .items(items)
                .total(result.totalHits())
                .size(size)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .totalIsExact(totalIsExact)
                .facets(searchFacetsService.buildFacets(request))
                .build();
    }

    // ────────────────────────── PostgreSQL fallback ──────────────────────────

    private SearchResponse searchViaDatabase(SearchRequest request,
                                             SearchCursor cursor,
                                             int size,
                                             String sortKey,
                                             String fingerprint) {
        int offset = cursor != null ? cursor.offset() : 0;
        int maxOffset = MAX_FALLBACK_PAGES * size;
        boolean hasMore = offset + size < maxOffset;

        Pageable pageable = PageRequest.of(offset / size, size, buildDbSort(sortKey));
        Specification<Product> spec = ProductSpecifications.fromFilterRequest(buildFilter(request))
                .and(ProductSpecifications.isActiveWithApprovedStore());

        List<Product> products = productRepository.findAll(spec, pageable).getContent();
        List<Long> ids = products.stream().map(Product::getId).toList();
        List<RecommendedProductResponse> items = hydrate(ids);

        // Trang cuối hoặc vượt giới hạn sâu -> không cấp cursor nữa.
        if (products.size() < size || offset + size >= maxOffset) {
            hasMore = false;
        }

        String nextCursor = null;
        if (hasMore) {
            SearchCursor payload = cursorCodec.newCursor(
                    SearchCursor.CursorMode.DB_OFFSET,
                    null,
                    List.of(),
                    offset + items.size(),
                    sortKey,
                    fingerprint);
            nextCursor = cursorCodec.encode(payload);
        }

        long total = countForFacets(request);

        return SearchResponse.builder()
                .items(items)
                .total(total)
                .size(size)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .totalIsExact(true)
                .facets(searchFacetsService.buildFacets(request))
                .build();
    }

    // ────────────────────────── helpers ──────────────────────────

    /**
     * Hydrate danh sách id thành response — giữ đúng thứ tự relevance, bỏ id không tồn tại.
     */
    private List<RecommendedProductResponse> hydrate(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, ProductSearchRepository.PriceRange> priceRanges = loadPriceRanges(ids);
        Map<Long, String> images = loadPrimaryImages(ids);
        return productSearchRepository.hydrate(ids, priceRanges, images);
    }

    /**
     * Load min/max giá + tổng tồn kho bằng 1 truy vấn aggregate cho toàn bộ id.
     */
    private Map<Long, ProductSearchRepository.PriceRange> loadPriceRanges(List<Long> ids) {
        Map<Long, ProductSearchRepository.PriceRange> result = new HashMap<>();
        for (Object[] row : productVariantRepository.aggregatePriceAndStockByProductIds(ids)) {
            Long productId = (Long) row[0];
            BigDecimal min = (BigDecimal) row[1];
            BigDecimal max = (BigDecimal) row[2];
            Number stock = (Number) row[3];
            result.put(productId, new ProductSearchRepository.PriceRange(
                    min != null ? min : BigDecimal.ZERO,
                    max != null ? max : BigDecimal.ZERO,
                    stock != null ? stock.intValue() : 0));
        }
        return result;
    }

    /**
     * Load ảnh chính bằng 1 lần gọi batch cho toàn bộ id.
     */
    private Map<Long, String> loadPrimaryImages(List<Long> ids) {
        Map<Long, String> result = new HashMap<>();
        imageService.getPrimaryImagesByOwnerIds(ImageOwnerType.PRODUCT, ids)
                .forEach((id, image) -> {
                    if (image != null && image.getUrl() != null) {
                        result.put(id, image.getUrl());
                    }
                });
        return result;
    }

    private ProductFilterRequest buildFilter(SearchRequest req) {
        ProductFilterRequest.ProductFilterRequestBuilder builder = ProductFilterRequest.builder()
                .keyword(req.getQ())
                .minPrice(req.getPriceMin())
                .maxPrice(req.getPriceMax())
                .rating(req.getRating());
        builder.flashSaleOnly(isFlashSaleOnly(req));
        builder.hasDiscount(isVoucherOnly(req));
        return builder.build();
    }

    private Boolean isFlashSaleOnly(SearchRequest req) {
        return hasService(req, "flash-sale") ? Boolean.TRUE : null;
    }

    private Boolean isVoucherOnly(SearchRequest req) {
        return hasService(req, "voucher") ? Boolean.TRUE : null;
    }

    /**
     * Kiểm tra danh sách service flag có chứa key cụ thể hay không.
     *
     * <p>Dùng {@code key} (string) thay vì enum để tránh phải import
     * {@code product.enums.SearchService} — tên class trùng với interface
     * {@code search.service.SearchService} của chính package này.
     */
    private boolean hasService(SearchRequest req, String key) {
        if (req.getServices() == null) {
            return false;
        }
        return req.getServices().stream()
                .anyMatch(s -> key.equals(s.getKey()));
    }

    /**
     * Danh sách key của các service flag đang được chọn — dùng cho fingerprint.
     */
    private List<String> serviceKeys(SearchRequest req) {
        if (req.getServices() == null) {
            return List.of();
        }
        return req.getServices().stream()
                .map(com.b2c.flash_sale_b2c_UTC2.product.enums.SearchService::getKey)
                .toList();
    }

    private long countForFacets(SearchRequest request) {
        try {
            Specification<Product> spec = ProductSpecifications.fromFilterRequest(buildFilter(request))
                    .and(ProductSpecifications.isActiveWithApprovedStore());
            return productRepository.count(spec);
        } catch (Exception e) {
            log.warn("[SearchServiceImpl] count failed: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * Fingerprint của query — dùng để phát hiện client đổi filter/sort giữa chừng.
     */
    private String buildFingerprint(SearchRequest request, String sortKey) {
        List<String> subcategories = request.getSubcategories() == null
                ? List.of() : request.getSubcategories();
        List<String> cities = request.getCities() == null
                ? List.of() : request.getCities();
        List<String> services = serviceKeys(request);

        return cursorCodec.fingerprint(
                normalizeKeyword(request.getQ()),
                String.join(",", subcategories),
                String.join(",", cities),
                String.join(",", services),
                request.getPriceMin() == null ? "" : request.getPriceMin().toPlainString(),
                request.getPriceMax() == null ? "" : request.getPriceMax().toPlainString(),
                request.getRating() == null ? "" : String.valueOf(request.getRating()),
                sortKey);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Sort của nhánh fallback PostgreSQL — tương đương sort của OpenSearch.
     */
    private Sort buildDbSort(String sortKey) {
        return switch (sortKey) {
            case "newest" -> Sort.by(Sort.Order.desc("createdAt"));
            case "best_selling" -> Sort.by(Sort.Order.desc("totalSold"));
            case "price_asc" -> Sort.by(Sort.Order.asc("id"));
            case "price_desc" -> Sort.by(Sort.Order.desc("id"));
            default -> Sort.by(Sort.Order.desc("createdAt"));
        };
    }
}
