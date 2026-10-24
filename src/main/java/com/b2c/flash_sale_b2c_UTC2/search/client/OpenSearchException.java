package com.b2c.flash_sale_b2c_UTC2.search.client;

/**
 * Lỗi khi giao tiếp với OpenSearch — luôn dẫn tới nhánh fallback PostgreSQL.
 */
public class OpenSearchException extends RuntimeException {

    public OpenSearchException(String message) {
        super(message);
    }

    public OpenSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
