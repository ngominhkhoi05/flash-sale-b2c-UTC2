package com.b2c.flash_sale_b2c_UTC2.flashsale.exception;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;

public class LockAcquisitionException extends BusinessException {

    public LockAcquisitionException() {
        super(FlashSaleErrorCode.LOCK_ACQUISITION_FAILED);
    }

    public LockAcquisitionException(String customMessage) {
        super(FlashSaleErrorCode.LOCK_ACQUISITION_FAILED, customMessage);
    }

    public LockAcquisitionException(String customMessage, Throwable cause) {
        super(FlashSaleErrorCode.LOCK_ACQUISITION_FAILED, customMessage, cause);
    }
}
