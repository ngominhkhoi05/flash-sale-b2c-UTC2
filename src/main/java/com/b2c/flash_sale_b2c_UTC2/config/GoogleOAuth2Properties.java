package com.b2c.flash_sale_b2c_UTC2.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình Google OAuth2 (Authorization Code flow cho module Auth).
 * Giá trị đọc từ prefix "google.oauth2" trong application.yaml.
 * Client ID / Secret / Redirect URI bắt buộc lấy từ biến môi trường
 * theo AGENTS.md mục #30 (không hard-code secret trong source).
 */
@Configuration
@ConfigurationProperties(prefix = "google.oauth2")
@Getter
@Setter
public class GoogleOAuth2Properties {

    /** OAuth2 Client ID. */
    private String clientId;

    /** OAuth2 Client Secret. */
    private String clientSecret;

    /** Redirect URI khớp với Authorized redirect URI đã đăng ký ở Google Console. */
    private String redirectUri;

    /** Google token endpoint — đổi authorization code lấy id_token + access_token. */
    private String tokenEndpoint = "https://oauth2.googleapis.com/token";

    /** Google userinfo endpoint. */
    private String userInfoEndpoint = "https://www.googleapis.com/oauth2/v3/userinfo";

    /** Google JWKS endpoint — verify id_token signature. */
    private String jwksUri = "https://www.googleapis.com/oauth2/v3/certs";

    /** Issuer claim được Google ký trong id_token. */
    private String issuer = "https://accounts.google.com";
}