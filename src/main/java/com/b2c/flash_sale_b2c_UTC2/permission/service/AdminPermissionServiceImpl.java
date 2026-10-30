package com.b2c.flash_sale_b2c_UTC2.permission.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.permission.dto.AclPermissionResponse;
import com.b2c.flash_sale_b2c_UTC2.permission.dto.FeatureFlagResponse;
import com.b2c.flash_sale_b2c_UTC2.permission.dto.InternalRoleGroupResponse;
import com.b2c.flash_sale_b2c_UTC2.permission.dto.ModerateReviewRequest;
import com.b2c.flash_sale_b2c_UTC2.permission.dto.ReportedReviewResponse;
import com.b2c.flash_sale_b2c_UTC2.permission.dto.SystemRoleResponse;
import com.b2c.flash_sale_b2c_UTC2.permission.dto.ToggleFeatureFlagRequest;
import com.b2c.flash_sale_b2c_UTC2.review.entity.ProductReview;
import com.b2c.flash_sale_b2c_UTC2.review.exception.ReviewErrorCode;
import com.b2c.flash_sale_b2c_UTC2.review.repository.ProductReviewRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPermissionServiceImpl implements AdminPermissionService {

    private final UserRepository userRepository;
    private final ProductReviewRepository reviewRepository;

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    // In-memory Feature Flags state cache
    private final Map<String, Boolean> flagStates = new ConcurrentHashMap<>(Map.of(
            "flash_sale_rush_hour", true,
            "zalopay_sandbox_mode", true,
            "ai_smart_search", true,
            "multi_vendor_cart", true,
            "strict_fraud_detection", false
    ));

    private final Map<String, String> flagMaintenanceReasons = new ConcurrentHashMap<>();

    @Override
    @Transactional(readOnly = true)
    public List<SystemRoleResponse> getSystemRoles() {
        long totalUsers = userRepository.count();

        return List.of(
                SystemRoleResponse.builder()
                        .id("role-admin")
                        .code("ROLE_ADMIN")
                        .name("Quản trị viên toàn quyền")
                        .description("Toàn quyền quản trị hệ thống, duyệt gian hàng, cấu hình sàn và đối soát")
                        .userCount(3)
                        .build(),
                SystemRoleResponse.builder()
                        .id("role-seller")
                        .code("ROLE_SELLER")
                        .name("Chủ gian hàng")
                        .description("Quản lý sản phẩm, đơn hàng, tham gia Flash Sale và ví doanh thu")
                        .userCount(Math.max(1, totalUsers / 10))
                        .build(),
                SystemRoleResponse.builder()
                        .id("role-buyer")
                        .code("ROLE_BUYER")
                        .name("Khách hàng mua sắm")
                        .description("Tìm kiếm sản phẩm, giật Flash Sale, thanh toán và quản lý đơn hàng")
                        .userCount(totalUsers)
                        .build()
        );
    }

    @Override
    public List<InternalRoleGroupResponse> getInternalRoleGroups() {
        return List.of(
                InternalRoleGroupResponse.builder()
                        .id("group-ops")
                        .name("Vận hành sàn (Operations)")
                        .code("GROUP_OPERATIONS")
                        .description("Kiểm duyệt sản phẩm, hỗ trợ xử lý khiếu nại và tra cứu đơn")
                        .memberCount(8)
                        .permissions(List.of("PRODUCT_APPROVE", "FLASH_SALE_MANAGE", "ORDER_VIEW"))
                        .build(),
                InternalRoleGroupResponse.builder()
                        .id("group-finance")
                        .name("Tài chính & Kế toán")
                        .code("GROUP_FINANCE")
                        .description("Đối soát định kỳ, phê duyệt rút tiền ví và xuất báo cáo tài chính")
                        .memberCount(4)
                        .permissions(List.of("RECONCILIATION_LOCK", "WITHDRAWAL_APPROVE", "FINANCE_EXPORT"))
                        .build(),
                InternalRoleGroupResponse.builder()
                        .id("group-moderator")
                        .name("Kiểm duyệt nội dung (Moderation)")
                        .code("GROUP_MODERATOR")
                        .description("Kiểm duyệt đánh giá, bình luận và xử lý tài khoản vi phạm")
                        .memberCount(6)
                        .permissions(List.of("REVIEW_MODERATE", "USER_LOCK"))
                        .build()
        );
    }

    @Override
    public List<AclPermissionResponse> getAclPermissions() {
        return List.of(
                AclPermissionResponse.builder().code("PRODUCT_VIEW").name("Xem sản phẩm").module("PRODUCT").isSensitive(false).isReadOnly(true).build(),
                AclPermissionResponse.builder().code("PRODUCT_APPROVE").name("Duyệt / Từ chối sản phẩm").module("PRODUCT").isSensitive(true).isReadOnly(false).build(),
                AclPermissionResponse.builder().code("FLASH_SALE_MANAGE").name("Cấu hình khung giờ & Duyệt SKU").module("FLASH_SALE").isSensitive(true).isReadOnly(false).build(),
                AclPermissionResponse.builder().code("ORDER_VIEW").name("Tra cứu đơn hàng toàn sàn").module("ORDER_VOUCHER").isSensitive(false).isReadOnly(true).build(),
                AclPermissionResponse.builder().code("VOUCHER_CREATE").name("Tạo & Quản lý Voucher sàn").module("ORDER_VOUCHER").isSensitive(true).isReadOnly(false).build(),
                AclPermissionResponse.builder().code("RECONCILIATION_LOCK").name("Khóa sổ đối soát tài chính").module("SYSTEM").isSensitive(true).isReadOnly(false).build(),
                AclPermissionResponse.builder().code("REVIEW_MODERATE").name("Ẩn / Bỏ ẩn đánh giá vi phạm").module("SYSTEM").isSensitive(true).isReadOnly(false).build(),
                AclPermissionResponse.builder().code("FEATURE_FLAG_TOGGLE").name("Bật / Tắt cờ tính năng hệ thống").module("SYSTEM").isSensitive(true).isReadOnly(false).build()
        );
    }

    @Override
    public List<FeatureFlagResponse> getFeatureFlags() {
        List<FeatureFlagResponse> flags = new ArrayList<>();

        flags.add(FeatureFlagResponse.builder()
                .code("flash_sale_rush_hour")
                .name("Chế độ giờ cao điểm Flash Sale")
                .description("Kích hoạt cơ chế hàng đợi Redis Stream và tối ưu hóa bộ đệm in-memory")
                .module("FLASH_SALE")
                .groupCount(2)
                .isEnabled(flagStates.getOrDefault("flash_sale_rush_hour", true))
                .updatedAt(DATETIME_FORMATTER.format(Instant.now()))
                .updatedBy("admin@vibemart.vn")
                .maintenanceReason(flagMaintenanceReasons.get("flash_sale_rush_hour"))
                .build());

        flags.add(FeatureFlagResponse.builder()
                .code("zalopay_sandbox_mode")
                .name("Chế độ thử nghiệm ZaloPay Sandbox")
                .description("Chuyển hướng cổng thanh toán ZaloPay sang môi trường thử nghiệm")
                .module("PAYMENT")
                .groupCount(1)
                .isEnabled(flagStates.getOrDefault("zalopay_sandbox_mode", true))
                .updatedAt(DATETIME_FORMATTER.format(Instant.now()))
                .updatedBy("admin@vibemart.vn")
                .maintenanceReason(flagMaintenanceReasons.get("zalopay_sandbox_mode"))
                .build());

        flags.add(FeatureFlagResponse.builder()
                .code("ai_smart_search")
                .name("Tìm kiếm thông minh OpenSearch + Vector")
                .description("Bật tìm kiếm hybrid search ngữ nghĩa và gợi ý từ khóa tự động")
                .module("SEARCH")
                .groupCount(3)
                .isEnabled(flagStates.getOrDefault("ai_smart_search", true))
                .updatedAt(DATETIME_FORMATTER.format(Instant.now()))
                .updatedBy("admin@vibemart.vn")
                .maintenanceReason(flagMaintenanceReasons.get("ai_smart_search"))
                .build());

        flags.add(FeatureFlagResponse.builder()
                .code("strict_fraud_detection")
                .name("Chặn đặt hàng nghi vấn gian lận")
                .description("Tự động từ chối đặt giữ chỗ nếu IP/Thiết bị có hành vi spam request")
                .module("SECURITY")
                .groupCount(2)
                .isEnabled(flagStates.getOrDefault("strict_fraud_detection", false))
                .updatedAt(DATETIME_FORMATTER.format(Instant.now()))
                .updatedBy("admin@vibemart.vn")
                .maintenanceReason(flagMaintenanceReasons.get("strict_fraud_detection"))
                .build());

        return flags;
    }

    @Override
    public FeatureFlagResponse toggleFeatureFlag(String code, ToggleFeatureFlagRequest request) {
        flagStates.put(code, request.isEnabled());
        if (request.getMaintenanceReason() != null) {
            flagMaintenanceReasons.put(code, request.getMaintenanceReason());
        }
        log.info("[FEATURE_FLAG] Cờ '{}' đã chuyển sang isEnabled={}, reason='{}'", code, request.isEnabled(), request.getMaintenanceReason());

        return getFeatureFlags().stream()
                .filter(f -> f.getCode().equalsIgnoreCase(code))
                .findFirst()
                .orElse(FeatureFlagResponse.builder()
                        .code(code)
                        .isEnabled(request.isEnabled())
                        .maintenanceReason(request.getMaintenanceReason())
                        .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportedReviewResponse> getReportedReviews() {
        List<ProductReview> reviews = reviewRepository.findAll().stream().limit(20).toList();

        return reviews.stream().map(r -> {
            String reviewerName = r.getUser() != null ? r.getUser().getFullName() : "Người dùng";
            String reviewerInitials = reviewerName.length() >= 2 ? reviewerName.substring(0, 2).toUpperCase() : "ND";
            String productName = r.getProduct() != null ? r.getProduct().getName() : "Sản phẩm";
            String productThumbnail = "";

            String status = "PENDING_REVIEW";
            if ("HIDDEN".equalsIgnoreCase(r.getStatus())) {
                status = "HIDDEN";
            } else if ("VISIBLE".equalsIgnoreCase(r.getStatus())) {
                status = "RESTORED";
            }

            return ReportedReviewResponse.builder()
                    .id(String.valueOf(r.getId()))
                    .reviewerName(reviewerName)
                    .reviewerId("user-" + (r.getUser() != null ? r.getUser().getId() : "0"))
                    .reviewerInitials(reviewerInitials)
                    .rating(r.getRating() != null ? r.getRating().intValue() : 5)
                    .productName(productName)
                    .productThumbnail(productThumbnail)
                    .content(r.getComment())
                    .reportReason("Nội dung phản cảm / Quảng cáo sai lệch")
                    .reportCount(3)
                    .reportedAt(r.getCreatedAt() != null ? DATETIME_FORMATTER.format(r.getCreatedAt()) : "")
                    .status(status)
                    .reviewImages(List.of())
                    .build();
        }).toList();
    }

    @Override
    @Transactional
    public ReportedReviewResponse moderateReview(Long reviewId, ModerateReviewRequest request) {
        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));

        String newStatus = "VISIBLE";
        String displayStatus = "RESTORED";

        if ("hide".equalsIgnoreCase(request.getAction())) {
            newStatus = "HIDDEN";
            displayStatus = "HIDDEN";
        } else if ("keep".equalsIgnoreCase(request.getAction()) || "restore".equalsIgnoreCase(request.getAction())) {
            newStatus = "VISIBLE";
            displayStatus = "RESTORED";
        }

        review.setStatus(newStatus);
        review.setUpdatedAt(Instant.now());
        reviewRepository.save(review);

        log.info("[REVIEW_MODERATION] Đánh giá id={} đã xử lý action='{}', status mới='{}'", reviewId, request.getAction(), newStatus);

        return ReportedReviewResponse.builder()
                .id(String.valueOf(review.getId()))
                .reviewerName(review.getUser() != null ? review.getUser().getFullName() : "Người dùng")
                .reviewerId("user-" + (review.getUser() != null ? review.getUser().getId() : "0"))
                .reviewerInitials("ND")
                .rating(review.getRating() != null ? review.getRating().intValue() : 5)
                .productName(review.getProduct() != null ? review.getProduct().getName() : "Sản phẩm")
                .productThumbnail("")
                .content(review.getComment())
                .reportReason("Đã xử lý kiểm duyệt")
                .reportCount(0)
                .reportedAt(DATETIME_FORMATTER.format(Instant.now()))
                .status(displayStatus)
                .reviewImages(List.of())
                .build();
    }
}
