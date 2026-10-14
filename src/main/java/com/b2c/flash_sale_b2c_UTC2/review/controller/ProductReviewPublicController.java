package com.b2c.flash_sale_b2c_UTC2.review.controller;

import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.review.dto.ProductReviewsPageResponse;
import com.b2c.flash_sale_b2c_UTC2.review.service.ReviewService;
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
public class ProductReviewPublicController {

    private final ReviewService reviewService;

    @GetMapping("/api/v1/products/{productId}/reviews")
    public ResponseEntity<ApiResponse<ProductReviewsPageResponse>> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Boolean hasMedia,
            @PageableDefault(size = 10) Pageable pageable) {

        ProductReviewsPageResponse response = reviewService.getProductReviews(productId, rating, hasMedia, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lay danh sach danh gia thanh cong", response));
    }
}