package com.b2c.flash_sale_b2c_UTC2.voucher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminVoucherKpisResponse {
    private long totalVouchers;
    private long activeVouchers;
    private BigDecimal totalDistributedBudget;
    private BigDecimal usedBudget;
    private long totalUsages;
}
