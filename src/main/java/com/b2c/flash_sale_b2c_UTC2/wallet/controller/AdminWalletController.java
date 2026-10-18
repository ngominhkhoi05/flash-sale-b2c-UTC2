package com.b2c.flash_sale_b2c_UTC2.wallet.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.AdminWithdrawalResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.ApproveWithdrawalRequest;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.RejectWithdrawalRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/wallets")
@RequiredArgsConstructor
@Tag(name = "Admin Wallets", description = "APIs quản lý số dư và phê duyệt rút tiền cho Ban quản trị sàn")
@PreAuthorize("hasRole('ADMIN')")
public class AdminWalletController {

    private final WalletService walletService;

    @GetMapping("/withdrawals")
    @Operation(summary = "Danh sách yêu cầu rút tiền của Người bán", description = "Hỗ trợ phân trang và lọc theo trạng thái (PENDING, APPROVED, REJECTED, ALL)")
    public ResponseEntity<ApiResponse<PageResponse<AdminWithdrawalResponse>>> getWithdrawals(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long storeId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<AdminWithdrawalResponse> response = walletService.getAdminWithdrawals(status, storeId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách yêu cầu rút tiền thành công", response));
    }

    @PatchMapping("/withdrawals/{id}/approve")
    @Operation(summary = "Phê duyệt lệnh rút tiền của Người bán", description = "Trừ số dư đóng băng và đánh dấu lệnh rút APPROVED")
    public ResponseEntity<ApiResponse<AdminWithdrawalResponse>> approveWithdrawal(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody(required = false) ApproveWithdrawalRequest request
    ) {
        Long adminId = (userDetails != null && userDetails.getUser() != null) ? userDetails.getUser().getId() : null;
        AdminWithdrawalResponse response = walletService.approveWithdrawal(id, adminId, request);
        return ResponseEntity.ok(ApiResponse.success("Phê duyệt yêu cầu rút tiền thành công", response));
    }

    @PatchMapping("/withdrawals/{id}/reject")
    @Operation(summary = "Từ chối lệnh rút tiền của Người bán", description = "Hoàn lại số dư khả dụng và ghi nhận lý do từ chối")
    public ResponseEntity<ApiResponse<AdminWithdrawalResponse>> rejectWithdrawal(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RejectWithdrawalRequest request
    ) {
        Long adminId = (userDetails != null && userDetails.getUser() != null) ? userDetails.getUser().getId() : null;
        AdminWithdrawalResponse response = walletService.rejectWithdrawal(id, adminId, request);
        return ResponseEntity.ok(ApiResponse.success("Từ chối yêu cầu rút tiền thành công", response));
    }
}
