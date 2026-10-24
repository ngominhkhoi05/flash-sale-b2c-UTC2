package com.b2c.flash_sale_b2c_UTC2.search.dto;

import java.util.List;

/**
 * Payload của cursor phân trang (đã giải mã + xác thực HMAC).
 *
 * <p>Cursor là token <b>opaque</b> từ góc nhìn client: FE chỉ gửi lại chuỗi
 * {@code nextCursor} mà không biết nội dung bên trong. BE toàn quyền quản lý
 * vòng đời PIT và điểm dừng của phân trang.
 *
 * <p>Cấu trúc payload (xem {@code docs/opensearch-search-architecture.md} mục 2.2):
 * <pre>
 * {
 *   "v":    1,                 // version
 *   "mode": "OS_PIT",          // nguồn phân trang
 *   "pitId": "&lt;base64 pit id&gt;",
 *   "sa":   [ ...sort values ],// mảng search_after của hit cuối
 *   "offset": 20,              // offset tương ứng (dùng cho nhánh fallback DB)
 *   "sort": "relevance",       // sort key (phải khớp giữa các request)
 *   "fh":   "&lt;sha256 của q+filters&gt;", // fingerprint query
 *   "iat":  1790246400         // thời điểm phát hành
 * }
 * </pre>
 *
 * @param version phiên bản format payload (hiện tại = 1).
 * @param mode    nguồn phân trang (xem {@link CursorMode}).
 * @param pitId   id của PIT (chỉ dùng cho mode {@link CursorMode#OS_PIT}).
 * @param searchAfter mảng sort values của hit cuối (search_after).
 * @param offset  số item đã trả về trước đó — dùng cho nhánh fallback DB.
 * @param sortKey sort key đã dùng (đảm bảo request sau dùng cùng sort).
 * @param fingerprint fingerprint của q + filters (chống trộn kết quả 2 query).
 * @param issuedAt thời điểm phát hành cursor (epoch giây).
 */
public record SearchCursor(
        int version,
        CursorMode mode,
        String pitId,
        List<Object> searchAfter,
        int offset,
        String sortKey,
        String fingerprint,
        long issuedAt) {

    /** Phiên bản payload hiện tại. */
    public static final int CURRENT_VERSION = 1;

    /**
     * Nguồn phân trang.
     */
    public enum CursorMode {
        /** Phân trang bằng OpenSearch PIT + search_after. */
        OS_PIT,
        /** Phân trang bằng offset của PostgreSQL (fallback khi OpenSearch lỗi). */
        DB_OFFSET
    }
}
