package com.b2c.flash_sale_b2c_UTC2.search.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình kết nối OpenSearch cho full-text search.
 *
 * <p>Prefix: {@code opensearch.*}. Xem {@code docs/opensearch-search-architecture.md}.
 *
 * <p>Lưu ý vận hành:
 * <ul>
 *   <li>{@code sslTrustAll=true} CHỈ dùng cho local (demo cert tự ký của OpenSearch).
 *       Production PHẢI đặt {@code false} và dùng cert thật + truststore.</li>
 *   <li>{@code cursorSecret} là khoá ký HMAC cho cursor phân trang — bắt buộc có
 *       (tối thiểu 32 ký tự) nếu bật chức năng search.</li>
 * </ul>
 */
@Configuration
@ConfigurationProperties(prefix = "opensearch")
@Getter
@Setter
public class OpenSearchProperties {

    /** Host OpenSearch, ví dụ {@code https://localhost:9200} hoặc {@code https://opensearch:9200}. */
    private String host = "https://localhost:9200";

    /** Username đăng nhập OpenSearch. */
    private String username = "admin";

    /** Password đăng nhập OpenSearch. */
    private String password = "";

    /** Alias chỉ tới index thật — luôn query alias (không query index thật). */
    private String indexAlias = "products";

    /** Timeout kết nối (ms). */
    private int connectTimeoutMs = 2000;

    /** Timeout đọc response (ms) — fail-fast để chuyển sang fallback DB. */
    private int socketTimeoutMs = 3000;

    /**
     * Trust-all SSL — CHỈ cho local với demo cert tự ký.
     * Production phải để {@code false}.
     */
    private boolean sslTrustAll = false;

    /** Khoá ký HMAC-SHA256 cho cursor phân trang. */
    private String cursorSecret = "";

    /** Cấu hình search. */
    private Search search = new Search();

    @Getter
    @Setter
    public static class Search {

        /** Số item mỗi trang (size của request OpenSearch). */
        private int pageSize = 24;

        /** Thời gian giữ PIT — ví dụ {@code 2m}. */
        private String pitKeepAlive = "2m";

        /** Timeout cho 1 request search (ms) — fail-fast sang fallback DB. */
        private int requestTimeoutMs = 1500;

        /** Ngưỡng {@code track_total_hits}. */
        private int trackTotalHits = 10000;
    }
}
