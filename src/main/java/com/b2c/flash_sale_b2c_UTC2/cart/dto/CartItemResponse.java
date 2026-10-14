package com.b2c.flash_sale_b2c_UTC2.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {
    private Long id;
    private Long productId;
    private Long variantId;
    private String sku;
    private String productName;
    private String variantName;
    private String imageUrl;
    private BigDecimal price;
    private Integer stockQuantity;
    private Integer quantity;
    private BigDecimal itemSubtotal;
    private Long storeId;
    private String storeName;
}
