package com.b2c.flash_sale_b2c_UTC2.search.imagesearch.client.dto;

import java.util.List;

/**
 * Kết quả trích xuất vector cho 1 URL trong batch.
 */
public record BatchEmbeddingResult(
        String url,
        boolean success,
        List<Float> vector,
        String error
) {}
