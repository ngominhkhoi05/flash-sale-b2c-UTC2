package com.b2c.flash_sale_b2c_UTC2.auth.exception;

import com.b2c.flash_sale_b2c_UTC2.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH_409_EMAIL", "Email này đã được đăng ký trên hệ thống"),
    PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH_409_PHONE", "Số điện thoại này đã được sử dụng"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_401_CREDENTIALS", "Email hoặc mật khẩu không chính xác"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_REFRESH_TOKEN", "Refresh token không hợp lệ hoặc đã hết hạn"),
    USER_DISABLED(HttpStatus.FORBIDDEN, "AUTH_403_USER_DISABLED", "Tài khoản của bạn đã bị khóa hoặc tạm ngưng"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_404_USER", "Tài khoản không tồn tại trên hệ thống"),
    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_404_ROLE", "Vai trò yêu cầu không tồn tại trên hệ thống"),

    // ===== Google OAuth2 (Authorization Code flow) =====
    GOOGLE_EXCHANGE_FAILED(HttpStatus.BAD_GATEWAY, "AUTH_502_GOOGLE_EXCHANGE",
            "Không thể trao đổi authorization code với Google"),
    GOOGLE_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_GOOGLE_TOKEN",
            "Google ID token không hợp lệ hoặc đã hết hạn"),
    GOOGLE_EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, "AUTH_401_GOOGLE_EMAIL_UNVERIFIED",
            "Email Google chưa được xác minh"),
    GOOGLE_CONFIG_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH_500_GOOGLE_CONFIG",
            "Thiếu cấu hình Google OAuth2 trên server");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
