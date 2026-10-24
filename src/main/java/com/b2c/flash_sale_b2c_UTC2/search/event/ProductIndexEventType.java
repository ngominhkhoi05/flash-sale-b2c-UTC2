package com.b2c.flash_sale_b2c_UTC2.search.event;

/**
 * Loại thay đổi cần đồng bộ sang index OpenSearch.
 */
public enum ProductIndexEventType {

    /** Sản phẩm được tạo hoặc cập nhật (variants, giá, tồn kho, tên...). */
    UPSERT,

    /** Sản phẩm bị xoá (hard delete) hoặc chuyển sang trạng thái ẩn (soft delete). */
    DELETE
}
