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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
@Tag(name = "Customer Wallet", description = "APIs ví điện tử dành cho Người dùng")
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/me")
    @Operation(summary = "Xem thông tin số dư ví cá nhân", description = "Lấy thông tin ví và số dư của người dùng hiện tại")
    public ResponseEntity<ApiResponse<SellerWalletResponse>> getMyWallet(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        SellerWalletResponse response = walletService.getCustomerWallet(userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin ví thành công", response));
    }

    @GetMapping("/me/transactions")
    @Operation(summary = "Xem lịch sử biến động số dư ví", description = "Hỗ trợ phân trang và lọc theo loại giao dịch")
    public ResponseEntity<ApiResponse<PageResponse<WalletTransactionResponse>>> getMyTransactions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String type,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<WalletTransactionResponse> response = walletService.getCustomerTransactions(
                userDetails.getUser().getId(), type, pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử giao dịch thành công", response));
    }

    @PostMapping("/me/withdraw")
    @Operation(summary = "Tạo yêu cầu rút tiền từ ví", description = "Rút tiền từ ví về tài khoản ngân hàng")
    public ResponseEntity<ApiResponse<WalletTransactionResponse>> withdraw(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody SellerWithdrawalRequest request
    ) {
        WalletTransactionResponse response = walletService.requestCustomerWithdrawal(
                userDetails.getUser().getId(), request
        );
        return ResponseEntity.ok(ApiResponse.success("Tạo yêu cầu rút tiền thành công", response));
    }
}
