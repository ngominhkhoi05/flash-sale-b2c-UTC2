package com.b2c.flash_sale_b2c_UTC2.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

/**
 * Response envelope for {@code GET /api/v1/products/search}.
 *
 * <p>Contract mới (cursor-based, xem {@code docs/opensearch-search-architecture.md}):
 * <ul>
 *   <li>Bỏ {@code page} — phân trang bằng cursor opaque, FE chỉ gửi lại
 *       {@code nextCursor}.</li>
 *   <li>Thêm {@code size}, {@code nextCursor}, {@code hasMore}, {@code totalIsExact}.</li>
 * </ul>
 *
 * <p>Shape là <b>giống nhau</b> cho cả 2 nhánh (OpenSearch và fallback PostgreSQL)
 * nên FE không cần biết nhánh nào đã chạy.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {
    /**
     * Items returned for the search query. Each item is a
     * {@link RecommendedProductResponse} (same shape as
     * {@code GET /api/v1/products/recommended}) so the FE can render
     * the storefront {@code ProductCard} variant without an extra mapping
     * step.
     */
    @Builder.Default
    private List<RecommendedProductResponse> items = Collections.emptyList();

    /** Tổng số kết quả khớp (có thể là xấp xỉ — xem {@link #totalIsExact}). */
    private long total;

    /** Số item tối đa mỗi trang. */
    private int size;

    /**
     * Cursor opaque cho trang tiếp theo — {@code null} nghĩa là đã hết kết quả.
     */
    private String nextCursor;

    /** {@code true} nếu còn trang tiếp theo. */
    private boolean hasMore;

    /**
     * {@code false} nếu {@code total} chỉ là xấp xỉ (bị cap bởi {@code track_total_hits}
     * hoặc có id bị DB loại bỏ trong lúc hydrate) — FE nên hiển thị "khoảng N kết quả".
     */
    @Builder.Default
    private boolean totalIsExact = true;

    /** Facets cho sidebar filter. */
    private SearchFacetsDto facets;
}
