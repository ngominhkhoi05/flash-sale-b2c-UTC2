package com.b2c.flash_sale_b2c_UTC2.review.exception;

import com.b2c.flash_sale_b2c_UTC2.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReviewErrorCode implements ErrorCode {
    REVIEW_PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW_404_PRODUCT_NOT_FOUND", "Khong tim thay san pham de xem danh gia"),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW_404_NOT_FOUND", "Khong tim thay danh gia"),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "REVIEW_409_ALREADY_EXISTS", "Ban da danh gia san pham nay roi"),
    REVIEW_ORDER_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "REVIEW_400_ORDER_NOT_COMPLETED", "Don hang chua hoan tat, khong the danh gia"),
    REVIEW_ACCESS_DENIED(HttpStatus.FORBIDDEN, "REVIEW_403_ACCESS_DENIED", "Ban khong co quyen thao tac voi danh gia nay"),
    REVIEW_FORBIDDEN(HttpStatus.FORBIDDEN, "REVIEW_403_FORBIDDEN", "Khong the thuc hien thao tac nay");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}