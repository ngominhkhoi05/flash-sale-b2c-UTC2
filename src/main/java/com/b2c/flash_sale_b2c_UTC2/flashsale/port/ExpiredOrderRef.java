package com.b2c.flash_sale_b2c_UTC2.flashsale.port;

/**
 * Tham chiếu đơn hàng PENDING_PAYMENT đã quá hạn cần hoàn kho Flash Sale.
 */
public record ExpiredOrderRef(
        Long orderId,
        String orderCode,
        Long flashSaleItemId,
        Long slotId,
        Long userId,
        Integer quantity
) {
}
