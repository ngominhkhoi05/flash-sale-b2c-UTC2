package com.b2c.flash_sale_b2c_UTC2.review.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.review.dto.ReplyReviewRequest;
import com.b2c.flash_sale_b2c_UTC2.review.dto.SellerReviewItemResponse;
import com.b2c.flash_sale_b2c_UTC2.review.dto.SellerReviewSummaryResponse;
import com.b2c.flash_sale_b2c_UTC2.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/reviews")
@RequiredArgsConstructor
@Tag(name = "Seller Reviews", description = "APIs quản lý và phản hồi đánh giá sản phẩm của gian hàng")
@PreAuthorize("hasRole('SELLER')")
public class SellerReviewController {

    private final ReviewService reviewService;

    @GetMapping("/summary")
    @Operation(summary = "Lấy thống kê đánh giá của shop", description = "Xem điểm trung bình, tổng đánh giá, tỷ lệ phản hồi, số đánh giá chưa phản hồi và phân bổ sao")
    public ResponseEntity<ApiResponse<SellerReviewSummaryResponse>> getSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        SellerReviewSummaryResponse response = reviewService.getSellerReviewSummary(userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success("Lấy thống kê đánh giá thành công", response));
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách đánh giá của shop", description = "Hỗ trợ phân trang, lọc theo số sao và lọc theo trạng thái phản hồi (ALL, UNREPLIED, REPLIED)")
    public ResponseEntity<ApiResponse<PageResponse<SellerReviewItemResponse>>> getReviews(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) String replyStatus,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<SellerReviewItemResponse> response = reviewService.getSellerReviews(
                userDetails.getUser().getId(), rating, replyStatus, pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đánh giá thành công", response));
    }

    @PostMapping("/{id}/reply")
    @Operation(summary = "Phản hồi đánh giá của khách hàng", description = "Người bán gửi lời phản hồi cho một bài đánh giá thuộc sản phẩm của shop")
    public ResponseEntity<ApiResponse<SellerReviewItemResponse>> replyReview(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id,
            @Valid @RequestBody ReplyReviewRequest request
    ) {
        SellerReviewItemResponse response = reviewService.replySellerReview(
                userDetails.getUser().getId(), id, request
        );
        return ResponseEntity.ok(ApiResponse.success("Gửi phản hồi đánh giá thành công", response));
    }
}
