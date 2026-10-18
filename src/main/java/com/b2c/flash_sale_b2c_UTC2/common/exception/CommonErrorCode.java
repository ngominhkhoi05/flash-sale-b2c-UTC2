package com.b2c.flash_sale_b2c_UTC2.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Các mã lỗi chung cơ bản (Common Error Codes) dùng cho tầng skeleton.
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "SYS_400", "Dữ liệu yêu cầu không hợp lệ"),
    MALFORMED_JSON_REQUEST(HttpStatus.BAD_REQUEST, "SYS_400_MALFORMED_JSON", "Body request không phải JSON hợp lệ"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_401", "Chưa xác thực hoặc token không hợp lệ"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_403", "Không có quyền truy cập tài nguyên"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "SYS_404", "Không tìm thấy tài nguyên yêu cầu"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "SYS_405", "Phương thức HTTP không được hỗ trợ"),
    CONFLICT(HttpStatus.CONFLICT, "SYS_409", "Xảy ra xung đột tài nguyên"),
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "SYS_422", "Dữ liệu không vượt qua ràng buộc xác thực"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SYS_500", "Lỗi máy chủ nội bộ không xác định");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
