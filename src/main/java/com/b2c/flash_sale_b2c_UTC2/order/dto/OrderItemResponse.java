package com.b2c.flash_sale_b2c_UTC2.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private Long id;
    private Long variantId;
    private Long flashSaleItemId;
    private String productName;
    private String variantName;
    private String imageUrl;
    private BigDecimal priceAtPurchase;
    private Integer quantity;
    private BigDecimal itemSubtotal;
}
