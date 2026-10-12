package com.b2c.flash_sale_b2c_UTC2.voucher.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.ApplyVoucherRequest;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.VoucherCalculationResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.VoucherResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Vouchers Public", description = "Xem và kiểm tra áp dụng mã giảm giá")
@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    @Operation(summary = "Xem danh sách mã giảm giá toàn sàn", description = "Lấy danh sách mã giảm giá Platform khả dụng")
    @GetMapping("/platform")
    public ResponseEntity<ApiResponse<List<VoucherResponse>>> getPlatformVouchers() {
        List<VoucherResponse> response = voucherService.getPlatformVouchers();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách voucher sàn thành công", response));
    }

    @Operation(summary = "Xem danh sách mã giảm giá của gian hàng", description = "Lấy danh sách mã giảm giá thuộc một gian hàng")
    @GetMapping("/store/{storeId}")
    public ResponseEntity<ApiResponse<List<VoucherResponse>>> getStoreVouchers(@PathVariable("storeId") Long storeId) {
        List<VoucherResponse> response = voucherService.getStoreVouchers(storeId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách voucher gian hàng thành công", response));
    }

    @Operation(summary = "Kiểm tra và áp dụng thử voucher", description = "Tính toán số tiền giảm giá và số tiền thanh toán cuối cùng khi áp mã")
    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<VoucherCalculationResponse>> validateAndCalculate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ApplyVoucherRequest request
    ) {
        Long userId = userDetails != null ? userDetails.getUser().getId() : null;
        VoucherCalculationResponse response = voucherService.validateAndCalculate(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Áp dụng mã giảm giá thành công", response));
    }
}
