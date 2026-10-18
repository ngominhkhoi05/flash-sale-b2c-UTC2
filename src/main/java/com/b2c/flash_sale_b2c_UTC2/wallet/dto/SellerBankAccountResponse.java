package com.b2c.flash_sale_b2c_UTC2.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerBankAccountResponse {
    private Long id;
    private Long storeId;
    private String bankName;
    private String bankCode;
    private String accountNumber;
    private String accountHolder;
    private String branch;
    private boolean isVerified;
    private Instant updatedAt;
}
