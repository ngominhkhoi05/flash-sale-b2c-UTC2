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
public class AdminWithdrawalResponse {
    private Long id;
    private String requestCode;
    private Long walletId;
    private Long storeId;
    private String storeName;
    private BigDecimal amount;
    private String status;
    private String bankName;
    private String bankCode;
    private String accountNumber;
    private String accountHolder;
    private String branch;
    private String sellerNote;
    private String adminNote;
    private Instant createdAt;
    private Instant approvedAt;
    private Instant rejectedAt;
}
