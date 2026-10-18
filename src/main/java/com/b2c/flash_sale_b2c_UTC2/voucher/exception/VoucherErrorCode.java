package com.b2c.flash_sale_b2c_UTC2.voucher.exception;

import com.b2c.flash_sale_b2c_UTC2.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum VoucherErrorCode implements ErrorCode {
    VOUCHER_NOT_FOUND("VOUCHER_404", "Không tìm thấy mã giảm giá", HttpStatus.NOT_FOUND),
    VOUCHER_CODE_EXISTS("VOUCHER_409", "Mã giảm giá đã tồn tại trên hệ thống", HttpStatus.CONFLICT),
    VOUCHER_EXPIRED("VOUCHER_400_1", "Mã giảm giá đã hết hạn sử dụng", HttpStatus.BAD_REQUEST),
    VOUCHER_NOT_STARTED("VOUCHER_400_2", "Mã giảm giá chưa đến thời gian hiệu lực", HttpStatus.BAD_REQUEST),
    VOUCHER_OUT_OF_STOCK("VOUCHER_400_3", "Mã giảm giá đã hết lượt sử dụng", HttpStatus.BAD_REQUEST),
    VOUCHER_USER_LIMIT_EXCEEDED("VOUCHER_400_4", "Bạn đã dùng hết lượt cho phép đối với mã giảm giá này", HttpStatus.BAD_REQUEST),
    VOUCHER_MIN_AMOUNT_NOT_MET("VOUCHER_400_5", "Giá trị đơn hàng chưa đạt mức tối thiểu áp dụng mã", HttpStatus.BAD_REQUEST),
    VOUCHER_STORE_MISMATCH("VOUCHER_403", "Mã giảm giá của gian hàng không áp dụng cho đơn hàng gian hàng khác", HttpStatus.FORBIDDEN),
    VOUCHER_INACTIVE("VOUCHER_400_6", "Mã giảm giá đang bị tạm khóa", HttpStatus.BAD_REQUEST),
    INVALID_VOUCHER_DATES("VOUCHER_400_7", "Thời gian kết thúc phải sau thời gian bắt đầu", HttpStatus.BAD_REQUEST),
    INVALID_DISCOUNT_PERCENT("VOUCHER_400_8", "Tỷ lệ giảm giá theo % không được vượt quá 100%", HttpStatus.BAD_REQUEST),
    VOUCHER_ALREADY_SAVED("VOUCHER_409_SAVED", "Mã giảm giá đã có trong kho voucher của bạn", HttpStatus.CONFLICT),
    VOUCHER_CANNOT_DELETE("VOUCHER_400_9", "Không thể xóa mã giảm giá đã có lượt sử dụng", HttpStatus.BAD_REQUEST),
    INVALID_TOTAL_QUANTITY("VOUCHER_400_10", "Tổng số lượng phát hành không được nhỏ hơn số lượt đã sử dụng", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
