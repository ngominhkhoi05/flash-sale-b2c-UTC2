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
public class QrShopOrderResponse {
    private Long orderId;
    private Long storeId;
    private String storeName;
    private String storeBadge;
    private String orderCode;
    private BigDecimal amount;
    private String itemSummary;
}
