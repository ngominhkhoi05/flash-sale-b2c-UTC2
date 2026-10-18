package com.b2c.flash_sale_b2c_UTC2.voucher.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.CreateVoucherRequest;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.UpdateVoucherRequest;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.UpdateVoucherStatusRequest;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.VoucherResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Seller Vouchers", description = "Quản lý mã giảm giá của gian hàng")
@RestController
@RequestMapping("/api/v1/seller/vouchers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerVoucherController {

    private final VoucherService voucherService;

    @Operation(summary = "Tạo mã giảm giá mới cho gian hàng", description = "Seller tạo mã giảm giá riêng thuộc gian hàng mình sở hữu")
    @PostMapping
    public ResponseEntity<ApiResponse<VoucherResponse>> createStoreVoucher(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateVoucherRequest request
    ) {
        VoucherResponse response = voucherService.createVoucher(userDetails.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo mã giảm giá gian hàng thành công", response));
    }

    @Operation(summary = "Xem danh sách mã giảm giá của tôi", description = "Seller lấy danh sách mã giảm giá do gian hàng mình phát hành có phân trang và lọc theo trạng thái")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<VoucherResponse>>> getMyStoreVouchers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(name = "status", required = false) String status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<VoucherResponse> response = voucherService.getSellerVouchers(userDetails.getUser().getId(), status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách mã giảm giá gian hàng thành công", response));
    }

    @Operation(summary = "Cập nhật trạng thái mã giảm giá", description = "Seller tạm dừng (PAUSED) hoặc kích hoạt lại (ACTIVE) voucher")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<VoucherResponse>> updateVoucherStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateVoucherStatusRequest request
    ) {
        VoucherResponse response = voucherService.updateSellerVoucherStatus(userDetails.getUser().getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái mã giảm giá thành công", response));
    }

    @Operation(summary = "Xem chi tiết mã giảm giá của gian hàng", description = "Seller xem chi tiết mã giảm giá do gian hàng mình phát hành")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VoucherResponse>> getStoreVoucherDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id
    ) {
        VoucherResponse response = voucherService.getSellerVoucherDetail(userDetails.getUser().getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết mã giảm giá thành công", response));
    }

    @Operation(summary = "Chỉnh sửa mã giảm giá của gian hàng", description = "Seller cập nhật thông tin mã giảm giá của gian hàng")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VoucherResponse>> updateStoreVoucher(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateVoucherRequest request
    ) {
        VoucherResponse response = voucherService.updateSellerVoucher(userDetails.getUser().getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật mã giảm giá thành công", response));
    }

    @Operation(summary = "Xóa mã giảm giá của gian hàng", description = "Seller xóa mã giảm giá chưa có lượt sử dụng nào")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStoreVoucher(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id
    ) {
        voucherService.deleteSellerVoucher(userDetails.getUser().getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Xóa mã giảm giá thành công", null));
    }
}
