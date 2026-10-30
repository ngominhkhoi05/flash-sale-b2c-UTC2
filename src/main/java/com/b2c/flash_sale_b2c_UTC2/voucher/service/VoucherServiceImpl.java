package com.b2c.flash_sale_b2c_UTC2.voucher.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.exception.StoreErrorCode;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.exception.UserErrorCode;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.ApplyVoucherRequest;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.CreateVoucherRequest;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.SaveVoucherRequest;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.UserVoucherResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.VoucherCalculationResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.VoucherResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.entity.UserVoucher;
import com.b2c.flash_sale_b2c_UTC2.voucher.entity.Voucher;
import com.b2c.flash_sale_b2c_UTC2.voucher.entity.VoucherUsage;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.VoucherUsageHistoryResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.exception.VoucherErrorCode;
import com.b2c.flash_sale_b2c_UTC2.voucher.mapper.VoucherMapper;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.AdminVoucherKpisResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.UpdateVoucherRequest;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.UpdateVoucherStatusRequest;
import com.b2c.flash_sale_b2c_UTC2.voucher.repository.UserVoucherRepository;
import com.b2c.flash_sale_b2c_UTC2.voucher.repository.VoucherRepository;
import com.b2c.flash_sale_b2c_UTC2.voucher.repository.VoucherUsageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherServiceImpl implements VoucherService {

    private final VoucherRepository voucherRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final UserVoucherRepository userVoucherRepository;
    private final UserRepository userRepository;
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
        if ("PERCENTAGE".equals(discountType)) {
            discountType = "PERCENT";
        }
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
        } else {
            // Tự động gán Store của Seller nếu user sở hữu Store
            store = storeRepository.findByUserId(userId).orElse(null);
        }

        if (store != null && !"APPROVED".equalsIgnoreCase(store.getStatus())) {
            throw new BusinessException(StoreErrorCode.STORE_NOT_APPROVED);
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

    @Override
    @Transactional(readOnly = true)
    public List<UserVoucherResponse> getMyVouchers(Long userId) {
        if (userId == null) {
            return List.of();
        }

        List<UserVoucher> userVouchers = userVoucherRepository.findAllByUserIdWithVoucherAndStore(userId);
        if (userVouchers.isEmpty()) {
            return List.of();
        }

        List<Long> voucherIds = userVouchers.stream()
                .map(uv -> uv.getVoucher().getId())
                .distinct()
                .toList();

        Map<Long, Long> usageMap = new HashMap<>();
        List<Object[]> rows = voucherUsageRepository.countByUserIdAndVoucherIdIn(userId, voucherIds);
        for (Object[] r : rows) {
            if (r[0] != null && r[1] != null) {
                usageMap.put((Long) r[0], (Long) r[1]);
            }
        }

        return userVouchers.stream()
                .map(uv -> mapToUserVoucherResponse(uv.getVoucher(), usageMap.getOrDefault(uv.getVoucher().getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional
    public UserVoucherResponse saveVoucherToWallet(Long userId, SaveVoucherRequest request) {
        if (userId == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        if (request == null || request.getCode() == null || request.getCode().isBlank()) {
            throw new BusinessException(VoucherErrorCode.VOUCHER_NOT_FOUND);
        }

        String code = request.getCode().trim();
        Voucher voucher = voucherRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new BusinessException(VoucherErrorCode.VOUCHER_NOT_FOUND));

        Instant now = Instant.now();
        if ("INACTIVE".equalsIgnoreCase(voucher.getStatus())) {
            throw new BusinessException(VoucherErrorCode.VOUCHER_INACTIVE);
        }
        if (voucher.getStartTime() != null && now.isBefore(voucher.getStartTime())) {
            throw new BusinessException(VoucherErrorCode.VOUCHER_NOT_STARTED);
        }
        if (voucher.getEndTime() != null && now.isAfter(voucher.getEndTime())) {
            throw new BusinessException(VoucherErrorCode.VOUCHER_EXPIRED);
        }
        if (voucher.getUsedQuantity() != null && voucher.getTotalQuantity() != null
                && voucher.getUsedQuantity() >= voucher.getTotalQuantity()) {
            throw new BusinessException(VoucherErrorCode.VOUCHER_OUT_OF_STOCK);
        }

        if (userVoucherRepository.existsByUserIdAndVoucherId(userId, voucher.getId())) {
            throw new BusinessException(VoucherErrorCode.VOUCHER_ALREADY_SAVED);
        }

        long usedCount = voucherUsageRepository.countByVoucherIdAndUserId(voucher.getId(), userId);
        if (voucher.getUserUsageLimit() != null && usedCount >= voucher.getUserUsageLimit()) {
            throw new BusinessException(VoucherErrorCode.VOUCHER_USER_LIMIT_EXCEEDED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        UserVoucher userVoucher = UserVoucher.builder()
                .user(user)
                .voucher(voucher)
                .savedAt(now)
                .build();
        userVoucherRepository.save(userVoucher);
        log.info("Người dùng id={} đã lưu voucher code={} vào kho voucher", userId, voucher.getCode());

        return mapToUserVoucherResponse(voucher, usedCount);
    }

    private UserVoucherResponse mapToUserVoucherResponse(Voucher voucher, long usedByThisUser) {
        Instant now = Instant.now();
        String status = "ACTIVE";
        if (voucher.getUserUsageLimit() != null && usedByThisUser >= voucher.getUserUsageLimit()) {
            status = "USED";
        } else if (voucher.getEndTime() != null && now.isAfter(voucher.getEndTime())) {
            status = "EXPIRED";
        } else if ("EXPIRED".equalsIgnoreCase(voucher.getStatus()) || "INACTIVE".equalsIgnoreCase(voucher.getStatus())) {
            status = "EXPIRED";
        }

        String scope = voucher.getStore() == null ? "PLATFORM" : "SHOP";
        Long storeId = voucher.getStore() != null ? voucher.getStore().getId() : null;
        String storeName = voucher.getStore() != null ? voucher.getStore().getStoreName() : null;

        String label = buildVoucherLabel(voucher);
        String discountType = "PERCENT".equalsIgnoreCase(voucher.getDiscountType()) ? "PERCENT" : "FIXED";

        return UserVoucherResponse.builder()
                .id(voucher.getId())
                .code(voucher.getCode())
                .label(label)
                .scope(scope)
                .storeId(storeId)
                .storeName(storeName)
                .discountType(discountType)
                .discountValue(voucher.getDiscountValue())
                .maxDiscount(voucher.getMaxDiscountAmount())
                .minOrderAmount(voucher.getMinOrderAmount())
                .userUsageLimit(voucher.getUserUsageLimit())
                .usedQuantity(voucher.getUsedQuantity() != null ? voucher.getUsedQuantity() : 0)
                .totalQuantity(voucher.getTotalQuantity())
                .startTime(voucher.getStartTime())
                .endTime(voucher.getEndTime())
                .status(status)
                .build();
    }

    private String buildVoucherLabel(Voucher voucher) {
        StringBuilder sb = new StringBuilder();
        if ("PERCENT".equalsIgnoreCase(voucher.getDiscountType())) {
            sb.append("Giảm ").append(voucher.getDiscountValue().stripTrailingZeros().toPlainString()).append("%");
            if (voucher.getMaxDiscountAmount() != null && voucher.getMaxDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                sb.append(" tối đa ").append(formatCurrencyVnd(voucher.getMaxDiscountAmount()));
            }
        } else {
            sb.append("Giảm ").append(formatCurrencyVnd(voucher.getDiscountValue()));
        }

        if (voucher.getMinOrderAmount() != null && voucher.getMinOrderAmount().compareTo(BigDecimal.ZERO) > 0) {
            sb.append(" đơn từ ").append(formatCurrencyVnd(voucher.getMinOrderAmount()));
        }
        return sb.toString();
    }

    private String formatCurrencyVnd(BigDecimal amount) {
        if (amount == null) return "0đ";
        long val = amount.longValue();
        if (val >= 1000) {
            return (val / 1000) + "K";
        }
        return val + "đ";
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VoucherResponse> getSellerVouchers(Long sellerUserId, String status, Pageable pageable) {
        Store store = storeRepository.findByUserId(sellerUserId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        Page<Voucher> page = voucherRepository.findSellerVouchers(store.getId(), status, Instant.now(), pageable);
        List<VoucherResponse> content = page.getContent().stream().map(voucherMapper::toResponse).toList();
        return PageResponse.of(page, content);
    }

    @Override
    @Transactional
    public VoucherResponse updateSellerVoucherStatus(Long sellerUserId, Long voucherId, UpdateVoucherStatusRequest request) {
        Store store = storeRepository.findByUserId(sellerUserId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new BusinessException(VoucherErrorCode.VOUCHER_NOT_FOUND));

        if (voucher.getStore() == null || !voucher.getStore().getId().equals(store.getId())) {
            throw new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED);
        }

        String targetStatus = request.getStatus().trim().toUpperCase();
        if (!"ACTIVE".equals(targetStatus) && !"PAUSED".equals(targetStatus)) {
            throw new BusinessException(VoucherErrorCode.VOUCHER_INACTIVE);
        }

        if (voucher.getEndTime().isBefore(Instant.now())) {
            throw new BusinessException(VoucherErrorCode.VOUCHER_EXPIRED);
        }

        voucher.setStatus(targetStatus);
        voucher = voucherRepository.save(voucher);
        log.info("Seller userId={} cập nhật trạng thái voucher id={} thành {}", sellerUserId, voucherId, targetStatus);
        return voucherMapper.toResponse(voucher);
    }

    @Override
    @Transactional(readOnly = true)
    public VoucherResponse getSellerVoucherDetail(Long sellerUserId, Long voucherId) {
        Store store = storeRepository.findByUserId(sellerUserId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new BusinessException(VoucherErrorCode.VOUCHER_NOT_FOUND));

        if (voucher.getStore() == null || !voucher.getStore().getId().equals(store.getId())) {
            throw new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED);
        }

        return voucherMapper.toResponse(voucher);
    }

    @Override
    @Transactional
    public VoucherResponse updateSellerVoucher(Long sellerUserId, Long voucherId, UpdateVoucherRequest request) {
        Store store = storeRepository.findByUserId(sellerUserId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new BusinessException(VoucherErrorCode.VOUCHER_NOT_FOUND));

        if (voucher.getStore() == null || !voucher.getStore().getId().equals(store.getId())) {
            throw new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED);
        }

        if (request.getTotalQuantity() != null) {
            int used = voucher.getUsedQuantity() != null ? voucher.getUsedQuantity() : 0;
            if (request.getTotalQuantity() < used) {
                throw new BusinessException(VoucherErrorCode.INVALID_TOTAL_QUANTITY);
            }
            voucher.setTotalQuantity(request.getTotalQuantity());
        }

        Instant newStart = request.getStartTime() != null ? request.getStartTime() : voucher.getStartTime();
        Instant newEnd = request.getEndTime() != null ? request.getEndTime() : voucher.getEndTime();
        if (newStart != null && newEnd != null && !newEnd.isAfter(newStart)) {
            throw new BusinessException(VoucherErrorCode.INVALID_VOUCHER_DATES);
        }
        if (request.getStartTime() != null) {
            voucher.setStartTime(request.getStartTime());
        }
        if (request.getEndTime() != null) {
            voucher.setEndTime(request.getEndTime());
        }

        if (request.getDiscountType() != null && !request.getDiscountType().isBlank()) {
            String dt = request.getDiscountType().trim().toUpperCase();
            if ("PERCENTAGE".equals(dt)) {
                dt = "PERCENT";
            }
            voucher.setDiscountType(dt);
        }

        if (request.getDiscountValue() != null) {
            if ("PERCENT".equalsIgnoreCase(voucher.getDiscountType()) && request.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BusinessException(VoucherErrorCode.INVALID_DISCOUNT_PERCENT);
            }
            voucher.setDiscountValue(request.getDiscountValue());
        }

        if (request.getMinOrderAmount() != null) {
            voucher.setMinOrderAmount(request.getMinOrderAmount());
        }
        if (request.getMaxDiscountAmount() != null) {
            voucher.setMaxDiscountAmount(request.getMaxDiscountAmount());
        }
        if (request.getUserUsageLimit() != null) {
            voucher.setUserUsageLimit(request.getUserUsageLimit());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            voucher.setStatus(request.getStatus().trim().toUpperCase());
        }

        voucher = voucherRepository.save(voucher);
        log.info("Seller userId={} cập nhật voucher id={}", sellerUserId, voucherId);
        return voucherMapper.toResponse(voucher);
    }

    @Override
    @Transactional
    public void deleteSellerVoucher(Long sellerUserId, Long voucherId) {
        Store store = storeRepository.findByUserId(sellerUserId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new BusinessException(VoucherErrorCode.VOUCHER_NOT_FOUND));

        if (voucher.getStore() == null || !voucher.getStore().getId().equals(store.getId())) {
            throw new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED);
        }

        int used = voucher.getUsedQuantity() != null ? voucher.getUsedQuantity() : 0;
        if (used > 0 || voucherUsageRepository.existsByVoucherId(voucherId)) {
            throw new BusinessException(VoucherErrorCode.VOUCHER_CANNOT_DELETE);
        }

        userVoucherRepository.deleteByVoucherId(voucherId);
        voucherRepository.delete(voucher);
        log.info("Seller userId={} đã xóa vĩnh viễn voucher id={}", sellerUserId, voucherId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VoucherResponse> getAdminVouchers(String scope, String status, String search, Pageable pageable) {
        Page<Voucher> page = voucherRepository.findAdminVouchers(scope, status, search, Instant.now(), pageable);
        List<VoucherResponse> content = page.getContent().stream().map(voucherMapper::toResponse).toList();
        return PageResponse.of(page, content);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminVoucherKpisResponse getAdminVoucherKpis() {
        long totalVouchers = voucherRepository.count();
        long activeVouchers = voucherRepository.countActiveVouchers(Instant.now());
        BigDecimal totalDistributedBudget = voucherRepository.sumTotalDistributedBudget();
        BigDecimal usedBudget = voucherUsageRepository.sumTotalUsedBudget();
        long totalUsages = voucherRepository.sumTotalUsages();

        return AdminVoucherKpisResponse.builder()
                .totalVouchers(totalVouchers)
                .activeVouchers(activeVouchers)
                .totalDistributedBudget(totalDistributedBudget)
                .usedBudget(usedBudget)
                .totalUsages(totalUsages)
                .build();
    }

    @Override
    @Transactional
    public void deleteVoucher(Long voucherId) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new BusinessException(VoucherErrorCode.VOUCHER_NOT_FOUND));

        if (voucher.getUsedQuantity() != null && voucher.getUsedQuantity() > 0) {
            throw new BusinessException(VoucherErrorCode.VOUCHER_CANNOT_BE_DELETED);
        }

        voucherRepository.delete(voucher);
        log.info("Admin đã xóa thành công voucher id={}", voucherId);
    }

    @Override
    @Transactional
    public VoucherResponse updateAdminVoucherStatus(Long voucherId, UpdateVoucherStatusRequest request) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new BusinessException(VoucherErrorCode.VOUCHER_NOT_FOUND));

        String targetStatus = request.getStatus().trim().toUpperCase();
        if (!"ACTIVE".equals(targetStatus) && !"PAUSED".equals(targetStatus) && !"DISABLED".equals(targetStatus)) {
            throw new BusinessException(VoucherErrorCode.VOUCHER_INACTIVE);
        }

        voucher.setStatus(targetStatus);
        voucher = voucherRepository.save(voucher);
        log.info("Admin cập nhật trạng thái voucher id={} thành {}", voucherId, targetStatus);
        return voucherMapper.toResponse(voucher);
    }

    @Override
    @Transactional
    public VoucherResponse updateVoucher(Long voucherId, UpdateVoucherRequest request) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new BusinessException(VoucherErrorCode.VOUCHER_NOT_FOUND));

        if (request.getStartTime() != null) {
            voucher.setStartTime(request.getStartTime());
        }
        if (request.getEndTime() != null) {
            voucher.setEndTime(request.getEndTime());
        }
        if (voucher.getEndTime().isBefore(voucher.getStartTime()) || voucher.getEndTime().equals(voucher.getStartTime())) {
            throw new BusinessException(VoucherErrorCode.INVALID_VOUCHER_DATES);
        }

        if (request.getDiscountType() != null && !request.getDiscountType().isBlank()) {
            String discountType = request.getDiscountType().trim().toUpperCase();
            if ("PERCENTAGE".equals(discountType)) {
                discountType = "PERCENT";
            }
            voucher.setDiscountType(discountType);
        }

        if (request.getDiscountValue() != null) {
            if ("PERCENT".equals(voucher.getDiscountType()) && request.getDiscountValue().compareTo(new BigDecimal("100")) > 0) {
                throw new BusinessException(VoucherErrorCode.INVALID_DISCOUNT_PERCENT);
            }
            voucher.setDiscountValue(request.getDiscountValue());
        }

        if (request.getMinOrderAmount() != null) {
            voucher.setMinOrderAmount(request.getMinOrderAmount());
        }
        if (request.getMaxDiscountAmount() != null) {
            voucher.setMaxDiscountAmount(request.getMaxDiscountAmount());
        }
        if (request.getTotalQuantity() != null) {
            if (voucher.getUsedQuantity() != null && request.getTotalQuantity() < voucher.getUsedQuantity()) {
                throw new BusinessException(VoucherErrorCode.INVALID_TOTAL_QUANTITY);
            }
            voucher.setTotalQuantity(request.getTotalQuantity());
        }
        if (request.getUserUsageLimit() != null) {
            voucher.setUserUsageLimit(request.getUserUsageLimit());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            voucher.setStatus(request.getStatus().trim().toUpperCase());
        }

        voucher = voucherRepository.save(voucher);
        log.info("Cập nhật voucher id={} thành công", voucherId);
        return voucherMapper.toResponse(voucher);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoucherUsageHistoryResponse> getVoucherUsageHistory(Long voucherId) {
        voucherRepository.findById(voucherId)
                .orElseThrow(() -> new BusinessException(VoucherErrorCode.VOUCHER_NOT_FOUND));

        List<VoucherUsage> usages = voucherUsageRepository.findByVoucherIdWithDetails(voucherId);
        return usages.stream().map(u -> {
            String customerName = u.getUser() != null ? u.getUser().getFullName() : "Khách hàng";
            String customerEmail = u.getUser() != null ? u.getUser().getEmail() : "";
            String customerAvatar = "";
            String orderCode = u.getOrder() != null ? u.getOrder().getOrderCode() : "";
            String status = (u.getOrder() != null && ("CANCELLED_USER".equalsIgnoreCase(u.getOrder().getStatus()) || "CANCELLED_TIMEOUT".equalsIgnoreCase(u.getOrder().getStatus()))) 
                    ? "REFUNDED" : "SUCCESS";

            return VoucherUsageHistoryResponse.builder()
                    .id(String.valueOf(u.getId()))
                    .voucherId(String.valueOf(voucherId))
                    .voucherCode(u.getVoucher() != null ? u.getVoucher().getCode() : "")
                    .customerName(customerName)
                    .customerEmail(customerEmail)
                    .customerAvatar(customerAvatar)
                    .orderCode(orderCode)
                    .discountAmount(u.getDiscountAmount())
                    .usedAt(u.getUsedAt())
                    .status(status)
                    .build();
        }).toList();
    }
}
