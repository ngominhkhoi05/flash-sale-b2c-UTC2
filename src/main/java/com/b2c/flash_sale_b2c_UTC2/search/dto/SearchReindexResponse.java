package com.b2c.flash_sale_b2c_UTC2.search.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Kết quả full-reindex index OpenSearch.
 *
 * @param indexedCount Số document đã ghi **thành công** vào index.
 * @param indexCount    Số document đang có trong index sau khi reindex.
 *                      Luôn {@code >= 0} ở response — nếu OpenSearch không truy vấn
 *                      được, controller trả 503 thay vì trả payload này.
 */
@Getter
@Builder
public class SearchReindexResponse {

    /** Số document đã index thành công. */
    private final int indexedCount;

    /** Số document thực sự đang nằm trong index. */
    private final long indexCount;
}
