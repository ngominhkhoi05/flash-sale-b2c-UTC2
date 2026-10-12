package com.b2c.flash_sale_b2c_UTC2.banner.controller;

import com.b2c.flash_sale_b2c_UTC2.banner.dto.HomeBannersResponse;
import com.b2c.flash_sale_b2c_UTC2.banner.service.BannerService;
import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Banners", description = "Homepage banner management")
@RestController
@RequestMapping("/api/v1/banners")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @Operation(summary = "Get home page banners", description = "Public endpoint - no auth required")
    @GetMapping("/home")
    public ResponseEntity<ApiResponse<HomeBannersResponse>> getHomeBanners() {
        HomeBannersResponse response = bannerService.getHomeBanners();
        return ResponseEntity.ok(ApiResponse.success("Get banners successfully", response));
    }
}
