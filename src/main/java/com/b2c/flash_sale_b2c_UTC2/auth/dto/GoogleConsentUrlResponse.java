package com.b2c.flash_sale_b2c_UTC2.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response cho GET /api/v1/auth/google/url.
 * FE dung authorizationUrl de redirect user den Google consent screen.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleConsentUrlResponse {
    /** URL Google consent screen (FE redirect user vao day). */
    private String authorizationUrl;

    /** State token (optional, FE co the tao them de CSRF protection). */
    private String state;
}