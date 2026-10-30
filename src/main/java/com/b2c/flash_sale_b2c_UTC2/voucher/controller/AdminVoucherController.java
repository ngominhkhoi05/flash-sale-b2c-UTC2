package com.b2c.flash_sale_b2c_UTC2.voucher.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.AdminVoucherKpisResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.CreateVoucherRequest;
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
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.UpdateVoucherRequest;

@Tag(name = "Admin Vouchers", description = "Quản lý mã giảm giá toàn sàn của Admin")
@RestController
@RequestMapping("/api/v1/admin/vouchers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminVoucherController {

    private final VoucherService voucherService;

    @Operation(summary = "Tạo mã giảm giá toàn sàn (Platform Voucher)", description = "Admin tạo mã giảm giá áp dụng toàn sàn (storeId = null)")
    @PostMapping
    public ResponseEntity<ApiResponse<VoucherResponse>> createPlatformVoucher(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateVoucherRequest request
    ) {
        request.setStoreId(null); // Bắt buộc storeId = null cho Platform Voucher
        VoucherResponse response = voucherService.createVoucher(userDetails.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo mã giảm giá toàn sàn thành công", response));
    }

    @Operation(summary = "Lấy danh sách mã giảm giá toàn sàn", description = "Admin tra cứu voucher có lọc theo scope (PLATFORM/STORE), status, search và phân trang")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<VoucherResponse>>> getAdminVouchers(
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<VoucherResponse> response = voucherService.getAdminVouchers(scope, status, search, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách mã giảm giá thành công", response));
    }

    @Operation(summary = "Thống kê KPIs ngân sách Voucher toàn sàn", description = "Admin xem tổng voucher, ngân sách phân bổ, ngân sách đã dùng và số lượt sử dụng")
    @GetMapping("/kpis")
    public ResponseEntity<ApiResponse<AdminVoucherKpisResponse>> getAdminVoucherKpis() {
        AdminVoucherKpisResponse response = voucherService.getAdminVoucherKpis();
        return ResponseEntity.ok(ApiResponse.success("Lấy KPIs voucher thành công", response));
    }

    @Operation(summary = "Xóa mã giảm giá toàn sàn", description = "Admin xóa voucher chưa có lượt sử dụng thực tế")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteVoucher(@PathVariable Long id) {
        voucherService.deleteVoucher(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa mã giảm giá thành công", null));
    }

    @Operation(summary = "Bật/tắt trạng thái mã giảm giá toàn sàn", description = "Admin cập nhật trạng thái ACTIVE, PAUSED hoặc DISABLED")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<VoucherResponse>> updateVoucherStatus(
            @PathVariable Long id,
            @Valid @RequestBody com.b2c.flash_sale_b2c_UTC2.voucher.dto.UpdateVoucherStatusRequest request
    ) {
        VoucherResponse response = voucherService.updateAdminVoucherStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái voucher thành công", response));
    }

    @Operation(summary = "Cập nhật mã giảm giá toàn sàn", description = "Admin chỉnh sửa các thông số của voucher")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VoucherResponse>> updateVoucher(
            @PathVariable Long id,
            @Valid @RequestBody UpdateVoucherRequest request
    ) {
        VoucherResponse response = voucherService.updateVoucher(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật mã giảm giá thành công", response));
    }

    @Operation(summary = "Lấy lịch sử sử dụng của Voucher sàn", description = "Admin xem chi tiết các đơn hàng và người dùng đã áp mã voucher này")
    @GetMapping("/{id}/usages")
    public ResponseEntity<ApiResponse<java.util.List<com.b2c.flash_sale_b2c_UTC2.voucher.dto.VoucherUsageHistoryResponse>>> getVoucherUsageHistory(
            @PathVariable Long id
    ) {
        java.util.List<com.b2c.flash_sale_b2c_UTC2.voucher.dto.VoucherUsageHistoryResponse> response = voucherService.getVoucherUsageHistory(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử sử dụng voucher thành công", response));
    }
}
