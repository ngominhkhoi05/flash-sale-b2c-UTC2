package com.b2c.flash_sale_b2c_UTC2.review.service;

import com.b2c.flash_sale_b2c_UTC2.review.dto.ProductReviewsPageResponse;
import org.springframework.data.domain.Pageable;

public interface ReviewService {
    ProductReviewsPageResponse getProductReviews(Long productId, Integer rating, Boolean hasMedia, Pageable pageable);
}