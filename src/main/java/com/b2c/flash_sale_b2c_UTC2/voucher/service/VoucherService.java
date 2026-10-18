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

    /**
     * Lấy danh sách voucher đã lưu trong kho ví cá nhân của người dùng.
     *
     * @param userId ID người dùng
     * @return Danh sách voucher đã lưu
     */
    List<com.b2c.flash_sale_b2c_UTC2.voucher.dto.UserVoucherResponse> getMyVouchers(Long userId);

    /**
     * Lưu mã voucher vào kho ví cá nhân của người dùng.
     *
     * @param userId ID người dùng
     * @param request Yêu cầu chứa mã voucher
     * @return Thông tin voucher sau khi lưu
     */
    com.b2c.flash_sale_b2c_UTC2.voucher.dto.UserVoucherResponse saveVoucherToWallet(Long userId, com.b2c.flash_sale_b2c_UTC2.voucher.dto.SaveVoucherRequest request);

    com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse<VoucherResponse> getSellerVouchers(
            Long sellerUserId,
            String status,
            org.springframework.data.domain.Pageable pageable
    );

    VoucherResponse updateSellerVoucherStatus(
            Long sellerUserId,
            Long voucherId,
            com.b2c.flash_sale_b2c_UTC2.voucher.dto.UpdateVoucherStatusRequest request
    );

    VoucherResponse getSellerVoucherDetail(
            Long sellerUserId,
            Long voucherId
    );

    VoucherResponse updateSellerVoucher(
            Long sellerUserId,
            Long voucherId,
            com.b2c.flash_sale_b2c_UTC2.voucher.dto.UpdateVoucherRequest request
    );

    void deleteSellerVoucher(
            Long sellerUserId,
            Long voucherId
    );

    com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse<VoucherResponse> getAdminVouchers(
            String scope,
            String status,
            String search,
            org.springframework.data.domain.Pageable pageable
    );

    com.b2c.flash_sale_b2c_UTC2.voucher.dto.AdminVoucherKpisResponse getAdminVoucherKpis();

    void deleteVoucher(Long voucherId);

    VoucherResponse updateAdminVoucherStatus(
            Long voucherId,
            com.b2c.flash_sale_b2c_UTC2.voucher.dto.UpdateVoucherStatusRequest request
    );

    VoucherResponse updateVoucher(
            Long voucherId,
            com.b2c.flash_sale_b2c_UTC2.voucher.dto.UpdateVoucherRequest request
    );
}
