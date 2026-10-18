package com.b2c.flash_sale_b2c_UTC2.auth.service;

import com.b2c.flash_sale_b2c_UTC2.auth.dto.AuthResponse;
import com.b2c.flash_sale_b2c_UTC2.auth.dto.GoogleConsentUrlResponse;
import com.b2c.flash_sale_b2c_UTC2.auth.dto.GoogleOAuth2LoginRequest;

public interface GoogleOAuth2Service {

    /**
     * Xu ly dang nhap/dang ky bang Google OAuth2 (Authorization Code flow).
     * Flow: code -> id_token -> verify -> upsert user -> cap JWT.
     *
     * @param request chua authorization code va redirectUri
     * @return AuthResponse (access + refresh token + user info)
     */
    AuthResponse loginWithGoogle(GoogleOAuth2LoginRequest request);

    /**
     * Tao Google consent screen URL (FE redirect user vao day).
     * URL chua client_id, redirect_uri, response_type=code, scope=openid+email+profile.
     */
    GoogleConsentUrlResponse getConsentUrl();
}