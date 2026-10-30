package com.b2c.flash_sale_b2c_UTC2.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationOrderResponse {
    private String id;
    private String orderCode;
    private String settlementDate;
    private String storeId;
    private String storeName;
    private String storeInitials;
    private String paymentMethod;
    private BigDecimal totalAmount;
    private BigDecimal commissionRate;
    private boolean isCustomRate;
    private BigDecimal platformFee;
    private BigDecimal shopEarnings;
    private String status; // SETTLED, PENDING_DELIVERY, REFUNDED
}
