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
public class ReconciliationRefundResponse {
    private String id;
    private String orderCode;
    private String customerName;
    private String customerPhone;
    private BigDecimal refundAmount;
    private String reason;
    private String transactionCode;
    private String refundedAt;
    private String status; // REFUNDED_WALLET, REFUNDED_ATM, PROCESSING_BANK
}
