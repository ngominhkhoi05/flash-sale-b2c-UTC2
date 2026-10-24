package com.b2c.flash_sale_b2c_UTC2.search.exception;

import com.b2c.flash_sale_b2c_UTC2.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Mã lỗi của module tìm kiếm (full-text search trên OpenSearch).
 */
@Getter
@RequiredArgsConstructor
public enum SearchErrorCode implements ErrorCode {

    SEARCH_INDEX_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SEARCH_503_INDEX_UNAVAILABLE",
            "Dịch vụ tìm kiếm toàn văn đang không khả dụng, vui lòng thử lại sau"),
    SEARCH_REINDEX_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SEARCH_500_REINDEX_FAILED",
            "Không thể đồng bộ chỉ mục tìm kiếm, vui lòng thử lại sau");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
