package com.b2c.flash_sale_b2c_UTC2.flashsale.exception;

import com.b2c.flash_sale_b2c_UTC2.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FlashSaleErrorCode implements ErrorCode {

    SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "FS_404_SLOT_NOT_FOUND", "Không tìm thấy khung giờ Flash Sale"),
    SLOT_TIME_INVALID(HttpStatus.BAD_REQUEST, "FS_400_SLOT_TIME_INVALID", "Thời gian bắt đầu phải trước thời gian kết thúc"),
    SLOT_OVERLAP(HttpStatus.CONFLICT, "FS_409_SLOT_OVERLAP", "Khung giờ Flash Sale bị trùng lặp với khung giờ đã tồn tại"),
    SLOT_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "FS_400_SLOT_NOT_ACTIVE", "Khung giờ Flash Sale hiện không hoạt động"),
    SLOT_ALREADY_ENDED(HttpStatus.BAD_REQUEST, "FS_400_SLOT_ALREADY_ENDED", "Khung giờ Flash Sale đã kết thúc"),

    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "FS_404_ITEM_NOT_FOUND", "Không tìm thấy sản phẩm trong Flash Sale"),
    ITEM_ALREADY_REGISTERED(HttpStatus.CONFLICT, "FS_409_ITEM_ALREADY_REGISTERED", "Biến thể sản phẩm này đã được đăng ký trong khung giờ"),
    INVALID_FLASH_SALE_PRICE(HttpStatus.BAD_REQUEST, "FS_400_INVALID_PRICE", "Giá Flash Sale phải lớn hơn 0 và nhỏ hơn giá gốc của biến thể"),
    INVALID_ALLOCATED_STOCK(HttpStatus.BAD_REQUEST, "FS_400_INVALID_ALLOCATED_STOCK", "Số lượng tồn kho phân bổ cho Flash Sale không hợp lệ hoặc vượt quá tồn kho gốc"),
    INVALID_PURCHASE_LIMIT(HttpStatus.BAD_REQUEST, "FS_400_INVALID_PURCHASE_LIMIT", "Giới hạn mua của người dùng phải lớn hơn 0"),
    ITEM_NOT_PENDING_APPROVAL(HttpStatus.BAD_REQUEST, "FS_400_ITEM_NOT_PENDING", "Mục Flash Sale không ở trạng thái chờ duyệt"),
    INSUFFICIENT_BASE_STOCK(HttpStatus.CONFLICT, "FS_409_INSUFFICIENT_BASE_STOCK", "Tồn kho gốc của sản phẩm không đủ để phân bổ cho Flash Sale"),
    STORE_NOT_APPROVED(HttpStatus.FORBIDDEN, "FS_403_STORE_NOT_APPROVED", "Gian hàng chưa được duyệt, không thể tham gia Flash Sale"),
    NOT_STORE_OWNER(HttpStatus.FORBIDDEN, "FS_403_NOT_STORE_OWNER", "Bạn không có quyền quản lý sản phẩm hoặc gian hàng này"),

    OUT_OF_STOCK(HttpStatus.CONFLICT, "FS_409_OUT_OF_STOCK", "Sản phẩm Flash Sale đã hết hàng tồn kho"),
    PURCHASE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "FS_409_PURCHASE_LIMIT_EXCEEDED", "Bạn đã vượt quá giới hạn số lượng mua cho sản phẩm này trong phiên Flash Sale"),
    ADDRESS_REQUIRED(HttpStatus.BAD_REQUEST, "FS_400_ADDRESS_REQUIRED", "Vui lòng thêm địa chỉ nhận hàng trước khi tham gia Flash Sale"),
    ADDRESS_NOT_FOUND_OR_NOT_OWNED(HttpStatus.FORBIDDEN, "FS_403_ADDRESS_INVALID", "Địa chỉ giao hàng không hợp lệ hoặc không thuộc về người dùng"),
    IDEMPOTENCY_KEY_MISSING(HttpStatus.BAD_REQUEST, "FS_400_IDEMPOTENCY_KEY_MISSING", "Yêu cầu thiếu Idempotency-Key"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "FS_409_IDEMPOTENCY_CONFLICT", "Yêu cầu với Idempotency-Key này đang được xử lý, vui lòng không gửi lặp lại"),
    LOCK_ACQUISITION_FAILED(HttpStatus.TOO_MANY_REQUESTS, "FS_429_LOCK_TIMEOUT", "Hệ thống đang xử lý lượng giao dịch lớn cho sản phẩm này, vui lòng thử lại sau giây lát"),
    ORDER_CREATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FS_500_ORDER_FAILED", "Hệ thống bận khi khởi tạo đơn hàng Flash Sale"),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "FS_404_RESERVATION_NOT_FOUND", "Không tìm thấy thông tin giữ chỗ Flash Sale"),
    CANNOT_CANCEL_NON_PENDING_RESERVATION(HttpStatus.BAD_REQUEST, "FS_400_CANNOT_CANCEL_RESERVATION", "Chỉ có thể hủy giữ chỗ đối với đơn hàng đang chờ thanh toán");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

