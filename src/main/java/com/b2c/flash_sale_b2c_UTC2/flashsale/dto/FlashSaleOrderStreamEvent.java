package com.b2c.flash_sale_b2c_UTC2.flashsale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event message đẩy vào Redis Stream (flash_sale:order_stream)
 * khi người dùng giữ chỗ thành công trên Redis (Lua/Lock).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleOrderStreamEvent {
    private Long userId;
    private Long addressId;
    private Long flashSaleItemId;
    private Long slotId;
    private Long variantId;
    private Long storeId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String productName;
    private String variantName;
    private String orderCode;
    private Instant expiresAt;
    private String idempotencyKey;
    private Long createdAtEpochMs;
}
