package com.b2c.flash_sale_b2c_UTC2.review.controller;

import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.review.dto.ProductReviewsPageResponse;
import com.b2c.flash_sale_b2c_UTC2.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Review - Public API", description = "Xem đánh giá sản phẩm công khai (không cần đăng nhập)")
public class ProductReviewPublicController {

    private final ReviewService reviewService;

    @GetMapping("/api/v1/products/{productId}/reviews")
    @Operation(summary = "Danh sách đánh giá công khai của sản phẩm (Public)",
               description = "Trả về đánh giá có status VISIBLE kèm phân bố số sao và tổng số review có media.")
    public ResponseEntity<ApiResponse<ProductReviewsPageResponse>> getProductReviews(
            @Parameter(description = "ID sản phẩm cần xem đánh giá", example = "1")
            @PathVariable Long productId,

            @Parameter(description = "Lọc theo số sao (1-5). Bỏ trống = tất cả.")
            @RequestParam(value = "rating", required = false) Integer rating,

            @Parameter(description = "Chỉ lấy review có đính kèm ảnh/video (true) hoặc tất cả (false/null)")
            @RequestParam(value = "hasMedia", required = false) Boolean hasMedia,

            @Parameter(description = "Phân trang (page=0, size=10 mặc định, sort=createdAt,DESC mặc định)")
            @PageableDefault(size = 10) Pageable pageable) {

        ProductReviewsPageResponse response = reviewService.getProductReviews(productId, rating, hasMedia, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lay danh sach danh gia thanh cong", response));
    }
}