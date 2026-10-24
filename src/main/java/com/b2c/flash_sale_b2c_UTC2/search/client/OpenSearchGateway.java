package com.b2c.flash_sale_b2c_UTC2.search.client;

import com.b2c.flash_sale_b2c_UTC2.search.dto.SearchQuerySpec;
import com.b2c.flash_sale_b2c_UTC2.search.dto.SearchResult;

/**
 * Cổng giao tiếp với OpenSearch cho tìm kiếm.
 *
 * <p>Interface thuần để service có thể thay thế bằng fake trong test và để tách
 * tầng: {@code SearchServiceImpl} không cần biết chi tiết HTTP của OpenSearch.
 *
 * <p>Triển khai thật: {@link RealOpenSearchGateway} — dùng PIT + {@code search_after},
 * {@code _source: false} (chỉ lấy {@code _id}).
 */
public interface OpenSearchGateway {

    /**
     * Thực hiện tìm kiếm với PIT + {@code search_after}.
     *
     * <p>Nếu {@code spec.pitId()} khác null thì dùng PIT đó (đồng thời gia hạn),
     * ngược lại tạo PIT mới. Mọi lỗi (timeout, PIT hết hạn, connection error) đều
     * được ném ra dưới dạng {@link OpenSearchException} để service chuyển sang
     * fallback PostgreSQL.
     *
     * @param spec đặc tả truy vấn đã chuẩn hoá.
     * @return kết quả chứa product id theo thứ tự sort.
     * @throws OpenSearchException khi không thể thực hiện truy vấn.
     */
    SearchResult search(SearchQuerySpec spec);

    /**
     * Xoá PIT (dọn tài nguyên server-side khi user đóng trang).
     *
     * @param pitId id PIT cần xoá.
     */
    void deletePit(String pitId);
}
