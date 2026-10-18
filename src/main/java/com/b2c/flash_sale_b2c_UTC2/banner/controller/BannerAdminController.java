package com.b2c.flash_sale_b2c_UTC2.banner.controller;

import com.b2c.flash_sale_b2c_UTC2.banner.dto.BannerListItem;
import com.b2c.flash_sale_b2c_UTC2.banner.dto.BannerRequest;
import com.b2c.flash_sale_b2c_UTC2.banner.service.BannerService;
import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

@Tag(name = "Admin - Banners", description = "Banner management for admin")
@RestController
@RequestMapping("/api/v1/admin/banners")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class BannerAdminController {

    private final BannerService bannerService;

    @Operation(summary = "List banners", description = "Paginated list with optional type/active filters")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<BannerListItem>>> listBanners(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<BannerListItem> result = bannerService.listBanners(type, isActive, pageable);
        return ResponseEntity.ok(ApiResponse.success("Danh sach banner", result));
    }

    @Operation(summary = "Create banner")
    @PostMapping
    public ResponseEntity<ApiResponse<BannerListItem>> createBanner(
            @Valid @RequestBody BannerRequest request) {
        BannerListItem created = bannerService.createBanner(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tao banner thanh cong", created));
    }

    @Operation(summary = "Update banner")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BannerListItem>> updateBanner(
            @PathVariable Long id,
            @Valid @RequestBody BannerRequest request) {
        BannerListItem updated = bannerService.updateBanner(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cap nhat banner thanh cong", updated));
    }

    @Operation(summary = "Delete banner (soft delete)")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBanner(@PathVariable Long id) {
        bannerService.deleteBanner(id);
        return ResponseEntity.ok(ApiResponse.success("Xoa banner thanh cong", null));
    }

    @Operation(summary = "Change banner status (activate/deactivate)")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<BannerListItem>> changeStatus(
            @PathVariable Long id,
            @RequestParam Boolean isActive) {
        BannerListItem updated = bannerService.changeStatus(id, isActive);
        return ResponseEntity.ok(ApiResponse.success("Doi trang thai banner thanh cong", updated));
    }
}