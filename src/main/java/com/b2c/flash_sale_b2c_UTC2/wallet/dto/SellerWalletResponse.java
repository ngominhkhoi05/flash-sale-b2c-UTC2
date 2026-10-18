package com.b2c.flash_sale_b2c_UTC2.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerWalletResponse {
    private BigDecimal balance;
    private BigDecimal pendingBalance;
    private BigDecimal totalWithdrawn;
    private SellerBankAccount bankAccount;
}
