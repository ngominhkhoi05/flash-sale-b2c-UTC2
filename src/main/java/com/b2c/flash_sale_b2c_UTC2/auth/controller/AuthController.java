package com.b2c.flash_sale_b2c_UTC2.auth.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.dto.AuthResponse;
import com.b2c.flash_sale_b2c_UTC2.auth.dto.GoogleConsentUrlResponse;
import com.b2c.flash_sale_b2c_UTC2.auth.dto.GoogleOAuth2LoginRequest;
import com.b2c.flash_sale_b2c_UTC2.auth.dto.LoginRequest;
import com.b2c.flash_sale_b2c_UTC2.auth.dto.RefreshTokenRequest;
import com.b2c.flash_sale_b2c_UTC2.auth.dto.RegisterRequest;
import com.b2c.flash_sale_b2c_UTC2.auth.service.AuthService;
import com.b2c.flash_sale_b2c_UTC2.auth.service.GoogleOAuth2Service;
import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Quản lý xác thực tài khoản, đăng ký, đăng nhập và cấp phát JWT token")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GoogleOAuth2Service googleOAuth2Service;

    @Operation(summary = "Đăng ký tài khoản mới", description = "Đăng ký tài khoản với email, mật khẩu, họ tên và vai trò ban đầu")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký tài khoản thành công", response));
    }

    @Operation(summary = "Đăng nhập hệ thống", description = "Đăng nhập bằng email và mật khẩu để nhận Access Token và Refresh Token")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", response));
    }

    @Operation(summary = "Làm mới Access Token", description = "Sử dụng Refresh Token còn hiệu lực để cấp mới Access Token")
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Làm mới token thành công", response));
    }

    @Operation(summary = "Đăng xuất tài khoản", description = "Đăng xuất tài khoản khỏi hệ thống và thu hồi refresh token")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công", null));
    }

    @Operation(summary = "Đăng nhập/đăng ký bằng Google OAuth2 (Authorization Code flow)",
               description = "FE gọi sau khi user redirect từ Google consent screen về redirect_uri. BE đổi code lấy id_token, verify và cấp JWT.")
    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> loginWithGoogle(@Valid @RequestBody GoogleOAuth2LoginRequest request) {
        AuthResponse response = googleOAuth2Service.loginWithGoogle(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập bằng Google thành công", response));
    }

    @Operation(summary = "Lấy Google consent screen URL",
               description = "FE redirect user đến URL này để bắt đầu flow Google OAuth2. Scope: openid + email + profile.")
    @GetMapping("/google/url")
    public ResponseEntity<ApiResponse<GoogleConsentUrlResponse>> getGoogleConsentUrl() {
        return ResponseEntity.ok(ApiResponse.success("Lấy Google consent URL thành công", googleOAuth2Service.getConsentUrl()));
    }
}
