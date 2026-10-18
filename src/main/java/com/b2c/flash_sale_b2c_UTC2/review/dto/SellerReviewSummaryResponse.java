package com.b2c.flash_sale_b2c_UTC2.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerReviewSummaryResponse {
    private Double averageRating;
    private Long totalReviews;
    private Double responseRate;
    private Long unrepliedCount;
    private Map<Integer, Long> ratingBreakdown;
}
