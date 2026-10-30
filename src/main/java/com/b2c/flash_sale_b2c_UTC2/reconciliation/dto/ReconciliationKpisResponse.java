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
public class ReconciliationKpisResponse {
    private BigDecimal totalGmv;
    private double gmvGrowthPercent;
    private BigDecimal platformCommission;
    private double commissionPercent;
    private BigDecimal shopWalletSettled;
    private double shopWalletSettledPercent;
    private BigDecimal refundAmount;
    private double refundRatePercent;
    private long ordersCount;
    private long refundsCount;
    private long walletTransCount;
}
