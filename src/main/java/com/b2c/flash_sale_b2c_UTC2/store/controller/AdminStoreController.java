package com.b2c.flash_sale_b2c_UTC2.store.controller;

import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.store.dto.StoreResponse;
import com.b2c.flash_sale_b2c_UTC2.store.dto.UpdateStoreStatusRequest;
import com.b2c.flash_sale_b2c_UTC2.store.service.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Stores", description = "Quản trị viên phê duyệt và kiểm soát gian hàng")
@RestController
@RequestMapping("/api/v1/admin/stores")
@RequiredArgsConstructor
public class AdminStoreController {

    private final StoreService storeService;

    @Operation(summary = "Lấy danh sách gian hàng toàn sàn", description = "Admin tra cứu, lọc theo status/search và phân trang danh sách gian hàng")
    @org.springframework.web.bind.annotation.GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse<com.b2c.flash_sale_b2c_UTC2.store.dto.AdminStoreResponse>>> getAdminStores(
            @org.springframework.web.bind.annotation.RequestParam(name = "status", required = false) String status,
            @org.springframework.web.bind.annotation.RequestParam(name = "search", required = false) String search,
            @org.springframework.data.web.PageableDefault(size = 10, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) org.springframework.data.domain.Pageable pageable
    ) {
        var response = storeService.getAdminStores(status, search, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách gian hàng thành công", response));
    }

    @Operation(summary = "Phê duyệt hoặc khóa gian hàng", description = "Admin cập nhật trạng thái (APPROVED, BANNED, SUSPENDED). Khi APPROVED, tự động cấp quyền SELLER cho chủ shop")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StoreResponse>> updateStoreStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateStoreStatusRequest request
    ) {
        StoreResponse response = storeService.updateStoreStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái gian hàng thành công", response));
    }
}
