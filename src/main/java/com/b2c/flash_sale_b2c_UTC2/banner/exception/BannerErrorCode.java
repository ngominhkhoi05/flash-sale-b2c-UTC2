package com.b2c.flash_sale_b2c_UTC2.banner.exception;

import com.b2c.flash_sale_b2c_UTC2.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BannerErrorCode implements ErrorCode {

    BANNER_NOT_FOUND(HttpStatus.NOT_FOUND, "BANNER_404_NOT_FOUND", "Khong tim thay banner"),
    BANNER_INVALID_STATUS(HttpStatus.UNPROCESSABLE_ENTITY, "BANNER_422_INVALID_STATUS", "Trang thai banner khong hop le cho thao tac nay"),
    BANNER_INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "BANNER_400_INVALID_DATE_RANGE", "Ngay bat dau phai truoc ngay ket thuc"),
    BANNER_IMAGE_URL_BLANK(HttpStatus.BAD_REQUEST, "BANNER_400_IMAGE_URL_BLANK", "URL hinh anh banner khong duoc de trong");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}