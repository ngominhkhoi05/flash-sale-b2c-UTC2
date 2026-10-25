package com.b2c.flash_sale_b2c_UTC2.search.imagesearch.dto;

/**
 * Thống kê kết quả thực thi batch reindex vector ảnh.
 */
public record ImageReindexResponse(
        int totalScanned,
        int successCount,
        int failedCount,
        long durationMs
) {}
