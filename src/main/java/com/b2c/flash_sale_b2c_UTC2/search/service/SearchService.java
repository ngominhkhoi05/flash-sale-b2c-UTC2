package com.b2c.flash_sale_b2c_UTC2.search.service;

import com.b2c.flash_sale_b2c_UTC2.product.dto.SearchRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.SearchResponse;

/**
 * Tìm kiếm sản phẩm cho storefront {@code GET /api/v1/products/search}.
 *
 * <p>Chiến lược: OpenSearch là bộ lọc + bộ xếp hạng (trả về product id theo
 * thứ tự relevance), PostgreSQL là nguồn dữ liệu hiển thị (hydrate).
 * Khi OpenSearch lỗi/timeout → tự động fallback sang truy vấn PostgreSQL.
 *
 * <p>Phân trang dùng cursor opaque đã ký HMAC — client không thể tự chế ra
 * cursor hợp lệ và không cần biết vòng đời PIT.
 */
public interface SearchService {

    /**
     * Tìm kiếm sản phẩm.
     *
     * @param request tham số tìm kiếm đã chuẩn hoá từ query string.
     * @param cursor  cursor opaque cho trang tiếp theo (null → trang đầu).
     * @return response chứa items + facets + cursor kế tiếp.
     */
    SearchResponse search(SearchRequest request, String cursor);
}
