package com.b2c.flash_sale_b2c_UTC2.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body cho POST /api/v1/auth/google.
 * FE goi Google consent screen, nhan lai authorization code va gui code do len BE.
 * BE tu doi code lay id_token, verify signature va cap JWT.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleOAuth2LoginRequest {

    /**
     * Authorization code tu Google redirect ve (one-time use, ~10 phut).
     */
    @NotBlank(message = "Authorization code tu Google khong duoc de trong")
    private String code;

    /**
     * Redirect URI FE su dung khi goi Google consent screen.
     * Phai khop voi Authorized redirect URI da dang ky o Google Cloud Console.
     */
    @NotBlank(message = "redirectUri khong duoc de trong")
    private String redirectUri;
}