package com.b2c.flash_sale_b2c_UTC2.search.imagesearch.client;

import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.client.dto.BatchEmbeddingResult;

import java.util.List;

/**
 * Cổng giao tiếp với microservice AI trích xuất vector embedding.
 */
public interface ImageEmbeddingGateway {

    /**
     * Trích xuất vector từ file ảnh upload (dạng byte array).
     */
    List<Float> embedImage(byte[] imageBytes, String filename, String contentType);

    /**
     * Trích xuất vector từ link ảnh URL.
     */
    List<Float> embedUrl(String imageUrl);

    /**
     * Trích xuất vector cho một mảng URLs (Batch Reindex).
     */
    List<BatchEmbeddingResult> embedBatchUrls(List<String> imageUrls);

    /**
     * Kiểm tra trạng thái hoạt động của AI service.
     */
    boolean isHealthy();
}
