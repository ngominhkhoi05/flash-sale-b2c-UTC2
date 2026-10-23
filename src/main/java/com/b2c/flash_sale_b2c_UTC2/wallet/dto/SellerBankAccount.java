package com.b2c.flash_sale_b2c_UTC2.wallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerBankAccount {
    private String bankName;
    private String bankCode;
    private String accountNumber;
    private String accountHolder;
    private String branch;

    @JsonProperty("isVerified")
    private boolean isVerified;
}

