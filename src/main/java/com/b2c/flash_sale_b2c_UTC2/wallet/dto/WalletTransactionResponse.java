package com.b2c.flash_sale_b2c_UTC2.wallet.dto;

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
public class WalletTransactionResponse {
    private Long id;
    private String transactionCode;
    private String orderCode;
    private String type;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String description;
    private String status;
    private Instant createdAt;
}
