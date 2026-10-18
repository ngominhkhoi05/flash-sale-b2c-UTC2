package com.b2c.flash_sale_b2c_UTC2.review.service;

import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.review.dto.ProductReviewsPageResponse;
import com.b2c.flash_sale_b2c_UTC2.review.dto.ReplyReviewRequest;
import com.b2c.flash_sale_b2c_UTC2.review.dto.SellerReviewItemResponse;
import com.b2c.flash_sale_b2c_UTC2.review.dto.SellerReviewSummaryResponse;
import org.springframework.data.domain.Pageable;

public interface ReviewService {

    ProductReviewsPageResponse getProductReviews(Long productId, Integer rating, Boolean hasMedia, Pageable pageable);

    SellerReviewSummaryResponse getSellerReviewSummary(Long sellerUserId);

    PageResponse<SellerReviewItemResponse> getSellerReviews(Long sellerUserId, Integer rating, String replyStatus, Pageable pageable);

    SellerReviewItemResponse replySellerReview(Long sellerUserId, Long reviewId, ReplyReviewRequest request);
}