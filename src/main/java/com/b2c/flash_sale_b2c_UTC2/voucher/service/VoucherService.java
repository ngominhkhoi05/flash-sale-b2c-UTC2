package com.b2c.flash_sale_b2c_UTC2.voucher.service;

import com.b2c.flash_sale_b2c_UTC2.voucher.dto.ApplyVoucherRequest;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.CreateVoucherRequest;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.VoucherCalculationResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.VoucherResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.entity.Voucher;

import java.math.BigDecimal;
import java.util.List;

public interface VoucherService {

    VoucherResponse createVoucher(Long userId, CreateVoucherRequest request);

    List<VoucherResponse> getPlatformVouchers();

    List<VoucherResponse> getStoreVouchers(Long storeId);

    List<VoucherResponse> getMyStoreVouchers(Long sellerUserId);

    VoucherCalculationResponse validateAndCalculate(Long userId, ApplyVoucherRequest request);

    Voucher validateVoucherForOrder(String voucherCode, Long userId, Long storeId, BigDecimal orderSubtotal);

    BigDecimal calculateDiscountAmount(Voucher voucher, BigDecimal subtotal);

    /**
     * Bồi hoàn lại lượt dùng của voucher và xóa bản ghi voucher_usages khi đơn hàng bị hủy.
     *
     * @param orderId ID đơn hàng bị hủy
     */
    void rollbackVoucherUsage(Long orderId);
}
