package com.b2c.flash_sale_b2c_UTC2.wallet.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.SellerWalletResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.SellerWithdrawalRequest;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.WalletTransactionResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/wallet")
@RequiredArgsConstructor
@Tag(name = "Seller Wallet", description = "APIs quản lý ví doanh thu và rút tiền dành cho Người bán")
@PreAuthorize("hasRole('SELLER')")
public class SellerWalletController {

    private final WalletService walletService;

    @GetMapping
    @Operation(summary = "Lấy thông tin ví & tài khoản ngân hàng của shop", description = "Trả về số dư khả dụng, số dư chờ duyệt, tổng đã rút và thông tin ngân hàng liên kết")
    public ResponseEntity<ApiResponse<SellerWalletResponse>> getMyWallet(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        SellerWalletResponse response = walletService.getSellerWallet(userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin ví thành công", response));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Lấy danh sách lịch sử giao dịch ví", description = "Hỗ trợ phân trang và lọc theo loại giao dịch (ORDER_SETTLEMENT, WITHDRAWAL, REFUND, v.v.)")
    public ResponseEntity<ApiResponse<PageResponse<WalletTransactionResponse>>> getTransactions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String type,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<WalletTransactionResponse> response = walletService.getSellerTransactions(
                userDetails.getUser().getId(), type, pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử giao dịch thành công", response));
    }

    @PostMapping("/withdraw")
    @Operation(summary = "Tạo yêu cầu rút tiền về tài khoản ngân hàng", description = "Trừ số dư khả dụng và ghi nhận giao dịch rút tiền")
    public ResponseEntity<ApiResponse<WalletTransactionResponse>> requestWithdrawal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody SellerWithdrawalRequest request
    ) {
        WalletTransactionResponse response = walletService.requestSellerWithdrawal(
                userDetails.getUser().getId(), request
        );
        return ResponseEntity.ok(ApiResponse.success("Tạo yêu cầu rút tiền thành công", response));
    }
}
