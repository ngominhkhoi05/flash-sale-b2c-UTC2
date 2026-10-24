package com.b2c.flash_sale_b2c_UTC2.search.dto;

import java.util.List;

/**
 * Kết quả một lần search OpenSearch — chỉ chứa product id theo thứ tự relevance.
 *
 * <p>Đây là kết quả của mô hình <b>search-then-hydrate</b>: OpenSearch chỉ là
 * bộ lọc + bộ xếp hạng, dữ liệu hiển thị luôn được hydrate từ PostgreSQL.
 *
 * @param productIds     danh sách product id theo đúng thứ tự sort của OpenSearch.
 * @param pitId          PIT id mới (PIT được gia hạn sau mỗi request → id thay đổi).
 * @param totalHits      tổng số hit (có thể bị cap → xem {@link #totalIsExact()}).
 * @param totalIsExact   {@code false} khi total bị cap bởi {@code track_total_hits}.
 * @param hasMore        còn trang tiếp theo hay không.
 * @param lastSortValues mảng sort values của hit cuối — dùng làm {@code search_after}
 *                       cho request kế tiếp. Rỗng nếu không có hit nào.
 */
public record SearchResult(
        List<Long> productIds,
        String pitId,
        long totalHits,
        boolean totalIsExact,
        boolean hasMore,
        List<Object> lastSortValues) {
}
