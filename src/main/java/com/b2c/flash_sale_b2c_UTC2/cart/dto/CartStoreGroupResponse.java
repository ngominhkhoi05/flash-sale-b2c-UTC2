package com.b2c.flash_sale_b2c_UTC2.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartStoreGroupResponse {
    private Long storeId;
    private String storeName;
    private String storeBadge;
    private String estimatedDeliveryLabel;
    private String shippingMethodLabel;
    private BigDecimal shippingFee;
    private List<CartItemResponse> items;
    private BigDecimal storeSubtotal;
}
