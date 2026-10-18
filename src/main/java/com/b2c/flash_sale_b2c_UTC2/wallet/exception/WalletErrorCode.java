package com.b2c.flash_sale_b2c_UTC2.wallet.exception;

import com.b2c.flash_sale_b2c_UTC2.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WalletErrorCode implements ErrorCode {

    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "WALLET_404_NOT_FOUND", "Không tìm thấy ví tiền của gian hàng"),
    WALLET_INSUFFICIENT(HttpStatus.BAD_REQUEST, "WALLET_400_INSUFFICIENT", "Số dư khả dụng trong ví không đủ để thực hiện giao dịch"),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "WALLET_400_INVALID_AMOUNT", "Số tiền giao dịch không hợp lệ"),
    MINIMUM_WITHDRAWAL_AMOUNT(HttpStatus.BAD_REQUEST, "WALLET_400_MIN_WITHDRAWAL", "Số tiền rút tối thiểu là 50,000 VND"),
    WALLET_BANK_NOT_CONFIGURED(HttpStatus.BAD_REQUEST, "WALLET_400_BANK_NOT_CONFIGURED", "Vui lòng cấu hình tài khoản ngân hàng trước khi rút tiền"),
    WITHDRAWAL_NOT_FOUND(HttpStatus.NOT_FOUND, "WALLET_404_WITHDRAWAL_NOT_FOUND", "Không tìm thấy yêu cầu rút tiền"),
    WITHDRAWAL_ALREADY_PROCESSED(HttpStatus.CONFLICT, "WALLET_409_WITHDRAWAL_ALREADY_PROCESSED", "Yêu cầu rút tiền này đã được xử lý trước đó");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
