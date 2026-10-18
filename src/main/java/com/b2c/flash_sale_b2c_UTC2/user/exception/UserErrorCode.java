package com.b2c.flash_sale_b2c_UTC2.user.exception;

import com.b2c.flash_sale_b2c_UTC2.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404_NOT_FOUND", "Không tìm thấy thông tin người dùng"),
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404_ADDRESS", "Không tìm thấy địa chỉ yêu cầu"),
    ADDRESS_ACCESS_DENIED(HttpStatus.FORBIDDEN, "USER_403_ADDRESS_DENIED", "Bạn không có quyền truy cập hoặc chỉnh sửa địa chỉ này"),
    OLD_PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST, "USER_400_OLD_PASSWORD", "Mật khẩu hiện tại không chính xác"),
    CANNOT_DELETE_DEFAULT_ADDRESS(HttpStatus.BAD_REQUEST, "USER_400_DELETE_DEFAULT", "Không thể xóa địa chỉ mặc định khi còn địa chỉ khác. Vui lòng đặt địa chỉ khác làm mặc định trước khi xóa"),
    CANNOT_LOCK_ADMIN(HttpStatus.BAD_REQUEST, "USER_400_CANNOT_LOCK_ADMIN", "Không thể khóa tài khoản có vai trò Quản trị viên"),
    INVALID_USER_STATUS(HttpStatus.BAD_REQUEST, "USER_400_INVALID_STATUS", "Trạng thái người dùng không hợp lệ (chỉ chấp nhận ACTIVE hoặc LOCKED)");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
