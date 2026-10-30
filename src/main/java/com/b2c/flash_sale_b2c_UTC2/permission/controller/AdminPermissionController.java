package com.b2c.flash_sale_b2c_UTC2.permission.controller;

import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.permission.dto.AclPermissionResponse;
import com.b2c.flash_sale_b2c_UTC2.permission.dto.FeatureFlagResponse;
import com.b2c.flash_sale_b2c_UTC2.permission.dto.InternalRoleGroupResponse;
import com.b2c.flash_sale_b2c_UTC2.permission.dto.ModerateReviewRequest;
import com.b2c.flash_sale_b2c_UTC2.permission.dto.ReportedReviewResponse;
import com.b2c.flash_sale_b2c_UTC2.permission.dto.SystemRoleResponse;
import com.b2c.flash_sale_b2c_UTC2.permission.dto.ToggleFeatureFlagRequest;
import com.b2c.flash_sale_b2c_UTC2.permission.service.AdminPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin Permissions & System", description = "Quản lý vai trò, nhóm quyền, cờ tính năng và kiểm duyệt cho Admin")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPermissionController {

    private final AdminPermissionService permissionService;

    @Operation(summary = "Lấy danh sách vai trò hệ thống", description = "Admin xem các vai trò ROLE_ADMIN, ROLE_SELLER, ROLE_BUYER kèm số lượng người dùng")
    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<SystemRoleResponse>>> getSystemRoles() {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách vai trò thành công", permissionService.getSystemRoles()));
    }

    @Operation(summary = "Lấy danh sách nhóm quyền nội bộ", description = "Admin xem các nhóm quyền vận hành, tài chính, kiểm duyệt")
    @GetMapping("/role-groups")
    public ResponseEntity<ApiResponse<List<InternalRoleGroupResponse>>> getInternalRoleGroups() {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách nhóm quyền thành công", permissionService.getInternalRoleGroups()));
    }

    @Operation(summary = "Lấy danh sách chi tiết các quyền ACL", description = "Admin tra cứu danh mục quyền theo từng module")
    @GetMapping("/permissions")
    public ResponseEntity<ApiResponse<List<AclPermissionResponse>>> getAclPermissions() {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh mục quyền hạn thành công", permissionService.getAclPermissions()));
    }

    @Operation(summary = "Lấy danh sách cờ tính năng", description = "Admin tra cứu các cờ tính năng động của hệ thống")
    @GetMapping("/feature-flags")
    public ResponseEntity<ApiResponse<List<FeatureFlagResponse>>> getFeatureFlags() {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách cờ tính năng thành công", permissionService.getFeatureFlags()));
    }

    @Operation(summary = "Bật/Tắt cờ tính năng", description = "Admin cấu hình trạng thái bật/tắt của cờ tính năng")
    @PatchMapping("/feature-flags/{code}")
    public ResponseEntity<ApiResponse<FeatureFlagResponse>> toggleFeatureFlag(
            @PathVariable String code,
            @RequestBody ToggleFeatureFlagRequest request
    ) {
        FeatureFlagResponse response = permissionService.toggleFeatureFlag(code, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật cờ tính năng thành công", response));
    }

    @Operation(summary = "Lấy danh sách đánh giá bị báo cáo", description = "Admin xem danh sách đánh giá sản phẩm bị khiếu nại")
    @GetMapping("/reviews/moderation")
    public ResponseEntity<ApiResponse<List<ReportedReviewResponse>>> getReportedReviews() {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đánh giá cần kiểm duyệt thành công", permissionService.getReportedReviews()));
    }

    @Operation(summary = "Kiểm duyệt đánh giá vi phạm", description = "Admin ẩn hoặc khôi phục hiển thị đánh giá sản phẩm")
    @PatchMapping("/reviews/{id}/moderation")
    public ResponseEntity<ApiResponse<ReportedReviewResponse>> moderateReview(
            @PathVariable Long id,
            @RequestBody ModerateReviewRequest request
    ) {
        ReportedReviewResponse response = permissionService.moderateReview(id, request);
        return ResponseEntity.ok(ApiResponse.success("Xử lý kiểm duyệt đánh giá thành công", response));
    }
}
