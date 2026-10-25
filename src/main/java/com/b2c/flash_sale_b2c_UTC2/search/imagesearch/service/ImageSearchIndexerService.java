package com.b2c.flash_sale_b2c_UTC2.search.imagesearch.service;

import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.dto.ImageReindexResponse;

/**
 * Service quản lý việc nạp và cập nhật vector embedding cho ảnh sản phẩm.
 */
public interface ImageSearchIndexerService {

    /**
     * Quét toàn bộ ảnh sản phẩm ACTIVE hiện có trong hệ thống và trích xuất vector embedding nạp vào database.
     */
    ImageReindexResponse reindexAllProductImages();

    /**
     * Nạp vector cho 1 ảnh đơn lẻ (dùng khi thêm mới hoặc cập nhật ảnh sản phẩm).
     */
    void indexProductImage(Long imageId, Long productId, String imageUrl);
}
