package com.b2c.flash_sale_b2c_UTC2.user.controller;

import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.user.dto.AdminUserResponse;
import com.b2c.flash_sale_b2c_UTC2.user.dto.UpdateUserStatusRequest;
import com.b2c.flash_sale_b2c_UTC2.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Users", description = "Quản trị viên quản lý và khóa tài khoản người dùng")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @Operation(summary = "Lấy danh sách người dùng toàn sàn", description = "Admin tra cứu, lọc theo role, status, tìm kiếm và phân trang người dùng")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> getAdminUsers(
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<AdminUserResponse> response = userService.getAdminUsers(role, status, search, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách người dùng thành công", response));
    }

    @Operation(summary = "Khóa hoặc mở khóa tài khoản người dùng", description = "Admin cập nhật trạng thái ACTIVE hoặc LOCKED. Khi LOCKED, tự động thu hồi toàn bộ refresh token trong Redis")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUserStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        AdminUserResponse response = userService.updateUserStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái người dùng thành công", response));
    }
}
