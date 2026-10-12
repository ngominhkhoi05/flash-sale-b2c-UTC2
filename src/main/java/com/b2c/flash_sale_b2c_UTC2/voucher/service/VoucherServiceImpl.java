package com.b2c.flash_sale_b2c_UTC2.voucher.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.exception.StoreErrorCode;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.ApplyVoucherRequest;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.CreateVoucherRequest;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.VoucherCalculationResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.VoucherResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.entity.Voucher;
import com.b2c.flash_sale_b2c_UTC2.voucher.exception.VoucherErrorCode;
import com.b2c.flash_sale_b2c_UTC2.voucher.mapper.VoucherMapper;
import com.b2c.flash_sale_b2c_UTC2.voucher.repository.VoucherRepository;
import com.b2c.flash_sale_b2c_UTC2.voucher.repository.VoucherUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherServiceImpl implements VoucherService {

    private final VoucherRepository voucherRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final StoreRepository storeRepository;
    private final VoucherMapper voucherMapper;

    @Override
    @Transactional
    public VoucherResponse createVoucher(Long userId, CreateVoucherRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (voucherRepository.existsByCode(code)) {
            throw new BusinessException(VoucherErrorCode.VOUCHER_CODE_EXISTS);
        }

        if (request.getEndTime().isBefore(request.getStartTime()) || request.getEndTime().equals(request.getStartTime())) {
            throw new BusinessException(VoucherErrorCode.INVALID_VOUCHER_DATES);
        }

        String discountType = request.getDiscountType().trim().toUpperCase();
        if ("PERCENT".equals(discountType) && request.getDiscountValue().compareTo(new BigDecimal("100")) > 0) {
            throw new BusinessException(VoucherErrorCode.INVALID_DISCOUNT_PERCENT);
        }

        Store store = null;
        if (request.getStoreId() != null) {
            store = storeRepository.findById(request.getStoreId())
                    .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

            // Verify seller ownership if creating store voucher
            if (!store.getUser().getId().equals(userId)) {
                throw new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED);
            }
        }

        Voucher voucher = Voucher.builder()
                .code(code)
                .store(store)
                .discountType(discountType)
                .discountValue(request.getDiscountValue())
                .minOrderAmount(request.getMinOrderAmount() != null ? request.getMinOrderAmount() : BigDecimal.ZERO)
                .maxDiscountAmount(request.getMaxDiscountAmount())
                .totalQuantity(request.getTotalQuantity())
                .usedQuantity(0)
                .userUsageLimit(request.getUserUsageLimit() != null ? request.getUserUsageLimit() : 1)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build();

        voucher = voucherRepository.save(voucher);
        log.info("Tạo voucher thành công code={}, storeId={}", code, store != null ? store.getId() : "PLATFORM");
        return voucherMapper.toResponse(voucher);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoucherResponse> getPlatformVouchers() {
        List<Voucher> vouchers = voucherRepository.findByStoreIdIsNull();
        return vouchers.stream().map(voucherMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoucherResponse> getStoreVouchers(Long storeId) {
        List<Voucher> vouchers = voucherRepository.findByStoreId(storeId);
        return vouchers.stream().map(voucherMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoucherResponse> getMyStoreVouchers(Long sellerUserId) {
        Store store = storeRepository.findByUserId(sellerUserId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));
        return getStoreVouchers(store.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public VoucherCalculationResponse validateAndCalculate(Long userId, ApplyVoucherRequest request) {
        Voucher voucher = validateVoucherForOrder(request.getCode(), userId, request.getStoreId(), request.getSubtotalAmount());
        BigDecimal discount = calculateDiscountAmount(voucher, request.getSubtotalAmount());
        BigDecimal finalAmount = request.getSubtotalAmount().subtract(discount).max(BigDecimal.ZERO);

        return VoucherCalculationResponse.builder()
                .voucherId(voucher.getId())
                .code(voucher.getCode())
                .discountAmount(discount)
                .finalAmount(finalAmount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Voucher validateVoucherForOrder(String voucherCode, Long userId, Long storeId, BigDecimal orderSubtotal) {
        Voucher voucher = voucherRepository.findByCode(voucherCode.trim().toUpperCase())
                .orElseThrow(() -> new BusinessException(VoucherErrorCode.VOUCHER_NOT_FOUND));

        if (!"ACTIVE".equalsIgnoreCase(voucher.getStatus())) {
            throw new BusinessException(VoucherErrorCode.VOUCHER_INACTIVE);
        }

        Instant now = Instant.now();
        if (now.isBefore(voucher.getStartTime())) {
            throw new BusinessException(VoucherErrorCode.VOUCHER_NOT_STARTED);
        }
        if (now.isAfter(voucher.getEndTime())) {
            throw new BusinessException(VoucherErrorCode.VOUCHER_EXPIRED);
        }

        if (voucher.getUsedQuantity() >= voucher.getTotalQuantity()) {
            throw new BusinessException(VoucherErrorCode.VOUCHER_OUT_OF_STOCK);
        }

        // Store matching check: If store_id IS NOT NULL, voucher.store.id MUST match order.store.id
        if (voucher.getStore() != null) {
            if (storeId == null || !voucher.getStore().getId().equals(storeId)) {
                throw new BusinessException(VoucherErrorCode.VOUCHER_STORE_MISMATCH);
            }
        }

        // Min order amount check
        if (voucher.getMinOrderAmount() != null && orderSubtotal.compareTo(voucher.getMinOrderAmount()) < 0) {
            throw new BusinessException(VoucherErrorCode.VOUCHER_MIN_AMOUNT_NOT_MET);
        }

        // User usage limit check
        if (userId != null && voucher.getUserUsageLimit() != null) {
            long usedCount = voucherUsageRepository.countByVoucherIdAndUserId(voucher.getId(), userId);
            if (usedCount >= voucher.getUserUsageLimit()) {
                throw new BusinessException(VoucherErrorCode.VOUCHER_USER_LIMIT_EXCEEDED);
            }
        }

        return voucher;
    }

    @Override
    public BigDecimal calculateDiscountAmount(Voucher voucher, BigDecimal subtotal) {
        if (voucher == null || subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal discount;
        if ("FIXED_AMOUNT".equalsIgnoreCase(voucher.getDiscountType())) {
            discount = voucher.getDiscountValue();
        } else if ("PERCENT".equalsIgnoreCase(voucher.getDiscountType())) {
            discount = subtotal.multiply(voucher.getDiscountValue())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            if (voucher.getMaxDiscountAmount() != null && discount.compareTo(voucher.getMaxDiscountAmount()) > 0) {
                discount = voucher.getMaxDiscountAmount();
            }
        } else {
            discount = BigDecimal.ZERO;
        }

        return discount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional
    public void rollbackVoucherUsage(Long orderId) {
        if (orderId == null) {
            return;
        }

        voucherUsageRepository.findByOrderId(orderId).ifPresent(usage -> {
            Voucher voucher = usage.getVoucher();
            if (voucher != null) {
                int currentUsed = voucher.getUsedQuantity() != null ? voucher.getUsedQuantity() : 1;
                int newUsed = Math.max(0, currentUsed - 1);
                voucher.setUsedQuantity(newUsed);
                voucherRepository.save(voucher);
                log.info("Bồi hoàn lượt dùng voucher id={}, code={}, usedQuantity giảm từ {} xuống {}",
                        voucher.getId(), voucher.getCode(), currentUsed, newUsed);
            }
            voucherUsageRepository.delete(usage);
            log.info("Đã xóa bản ghi voucher_usages liên kết với orderId={}", orderId);
        });
    }
}
