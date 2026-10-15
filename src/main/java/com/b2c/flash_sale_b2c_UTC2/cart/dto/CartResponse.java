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
public class CartResponse {
    private Long id;
    private Long userId;
    private List<CartStoreGroupResponse> storeGroups;
    private Integer totalItems;
    private BigDecimal subtotal;
    private BigDecimal totalShippingFee;
    private BigDecimal totalAmount;
    private BigDecimal grandTotal;
}
