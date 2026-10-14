package com.b2c.flash_sale_b2c_UTC2.image.exception;

import com.b2c.flash_sale_b2c_UTC2.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Mã lỗi cho Image Module (theo docs/api/api-document.md mục 28.5).
 */
@Getter
@RequiredArgsConstructor
public enum ImageErrorCode implements ErrorCode {

    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_413_FILE_TOO_LARGE", "Tệp ảnh vượt quá kích thước cho phép (tối đa 5MB)"),
    INVALID_FORMAT(HttpStatus.BAD_REQUEST, "IMAGE_400_INVALID_FORMAT", "Định dạng tệp ảnh không hợp lệ (chỉ chấp nhận HTTP/PNG/WebP)"),
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "IMAGE_400_EMPTY_FILE", "Tệp ảnh rỗng, vui lòng chọn tệp khác"),
    NOT_OWNER(HttpStatus.FORBIDDEN, "IMAGE_403_NOT_OWNER", "Bạn không có quyền quản lý ảnh thuộc đối tượng này"),
    PRIMARY_EXISTS(HttpStatus.CONFLICT, "IMAGE_409_PRIMARY_EXISTS", "Đối tượng đã có ảnh đại diện chính ACTIVE khác"),
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "IMAGE_404_NOT_FOUND", "Không tìm thấy ảnh"),
    OWNER_NOT_FOUND(HttpStatus.NOT_FOUND, "IMAGE_404_OWNER_NOT_FOUND", "Không tìm thấy đối tượng sở hữu ảnh"),
    INVALID_OWNER_TYPE(HttpStatus.BAD_REQUEST, "IMAGE_400_INVALID_OWNER_TYPE", "ownerType không hợp lệ"),
    INVALID_PRIMARY_FOR_OWNER(HttpStatus.BAD_REQUEST, "IMAGE_400_PRIMARY_NOT_ALLOWED", "Chỉ PRODUCT/VARIANT mới hợp lệ với ảnh đại diện chính"),
    INVALID_ORDER_FOR_OWNER(HttpStatus.BAD_REQUEST, "IMAGE_400_ORDER_NOT_ALLOWED", "Chỉ REVIEW mới hợp lệ với cập nhật thứ tự hiển thị"),
    CLOUDINARY_FAILED(HttpStatus.BAD_GATEWAY, "IMAGE_502_CLOUDINARY_FAILED", "Cloudinary API thất bại, sẽ được thử lại bởi tác vụ định kỳ");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}