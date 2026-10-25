package com.b2c.flash_sale_b2c_UTC2.search.imagesearch.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình kết nối tới AI Image Embedding Microservice và các tham số tìm kiếm hình ảnh.
 */
@Configuration
@ConfigurationProperties(prefix = "app.image-search")
@Getter
@Setter
public class ImageSearchProperties {

    /** URL của service Python CLIP embedding. Mặc định localhost:8000. */
    private String embeddingServiceUrl = "http://localhost:8000";

    /** Timeout kết nối (ms). */
    private int connectTimeoutMs = 3000;

    /** Timeout đọc response (ms). */
    private int readTimeoutMs = 15000;

    /**
     * Ngưỡng khoảng cách tối đa (Cosine Distance) để coi 2 ảnh là tương đồng.
     * Vì vector đã được normalize (unit length):
     * - Cosine Distance = 1 - Cosine Similarity
     * - Giá trị 0.35 tương đương độ tương đồng >= 65%.
     */
    private double maxDistance = 0.38;

    /** Số sản phẩm tối đa trả về trong 1 lần tìm kiếm. */
    private int defaultLimit = 20;

    /** Kích thước mỗi batch khi Admin chạy Reindex. */
    private int reindexBatchSize = 50;
}
