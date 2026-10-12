package com.b2c.flash_sale_b2c_UTC2.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReviewResponse {

    private Long id;
    private Integer rating;
    private String comment;
    private String userName;
    private String variantSku;
    private Map<String, String> variantAttributes;
    private List<String> album;
    private String sellerReply;
    private Instant sellerReplyAt;
    private Instant createdAt;
}