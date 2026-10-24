package com.b2c.flash_sale_b2c_UTC2.search.event;

/**
 * Yêu cầu đồng bộ một sản phẩm sang index OpenSearch.
 *
 * <p>Được publish từ {@code ProductServiceImpl} bên trong transaction và được xử lý
 * bởi listener {@code @TransactionalEventListener(phase = AFTER_COMMIT)} - nhờ vậy không
 * bao giờ xảy ra trường hợp "transaction rollback nhưng document đã index" (dual-write hazard).
 *
 * @param productId ID sản phẩm cần đồng bộ.
 * @param type      Loại thay đổi (UPSERT | DELETE).
 */
public record ProductIndexEvent(Long productId, ProductIndexEventType type) {

    /** Tạo event đồng bộ (upsert) document của sản phẩm. */
    public static ProductIndexEvent upsert(Long productId) {
        return new ProductIndexEvent(productId, ProductIndexEventType.UPSERT);
    }

    /** Tạo event xoá document của sản phẩm khỏi index. */
    public static ProductIndexEvent delete(Long productId) {
        return new ProductIndexEvent(productId, ProductIndexEventType.DELETE);
    }
}
