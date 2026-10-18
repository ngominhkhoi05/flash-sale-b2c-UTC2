package com.b2c.flash_sale_b2c_UTC2.wallet.service;

import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.AdminWithdrawalResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.ApproveWithdrawalRequest;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.RejectWithdrawalRequest;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.SellerBankAccountRequest;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.SellerBankAccountResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.SellerWalletResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.SellerWithdrawalRequest;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.WalletTransactionResponse;
import org.springframework.data.domain.Pageable;

public interface WalletService {

    SellerWalletResponse getSellerWallet(Long userId);

    SellerBankAccountResponse getSellerBankAccount(Long userId);

    SellerBankAccountResponse updateSellerBankAccount(Long userId, SellerBankAccountRequest request);

    PageResponse<WalletTransactionResponse> getSellerTransactions(Long userId, String type, Pageable pageable);

    WalletTransactionResponse requestSellerWithdrawal(Long userId, SellerWithdrawalRequest request);

    PageResponse<AdminWithdrawalResponse> getAdminWithdrawals(String status, Long storeId, Pageable pageable);

    AdminWithdrawalResponse approveWithdrawal(Long withdrawalId, Long adminId, ApproveWithdrawalRequest request);

    AdminWithdrawalResponse rejectWithdrawal(Long withdrawalId, Long adminId, RejectWithdrawalRequest request);

    SellerWalletResponse getCustomerWallet(Long userId);

    PageResponse<WalletTransactionResponse> getCustomerTransactions(Long userId, String type, Pageable pageable);

    WalletTransactionResponse requestCustomerWithdrawal(Long userId, SellerWithdrawalRequest request);
}

