package com.b2c.flash_sale_b2c_UTC2.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReviewsPageResponse {

    private List<ProductReviewResponse> items;
    private RatingDistributionDto ratingDistribution;
    private Long mediaCount;
    private Integer totalElements;
    private Integer totalPages;
    private Integer page;
    private Integer size;
}