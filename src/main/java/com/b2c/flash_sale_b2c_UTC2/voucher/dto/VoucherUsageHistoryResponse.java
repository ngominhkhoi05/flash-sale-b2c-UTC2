package com.b2c.flash_sale_b2c_UTC2.voucher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherUsageHistoryResponse {
    private String id;
    private String voucherId;
    private String voucherCode;
    private String customerName;
    private String customerEmail;
    private String customerAvatar;
    private String orderCode;
    private BigDecimal discountAmount;
    private Instant usedAt;
    private String status; // SUCCESS hoặc REFUNDED
}
