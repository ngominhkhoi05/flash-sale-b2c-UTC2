package com.b2c.flash_sale_b2c_UTC2.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerReviewItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productImage;
    private String variantSku;
    private String variantName;
    private String orderCode;
    private String buyerName;
    private String buyerAvatar;
    private Integer rating;
    private String comment;
    private List<String> mediaUrls;
    private SellerReviewReplyDto sellerReply;
    private Instant createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SellerReviewReplyDto {
        private Long id;
        private String replyText;
        private Instant repliedAt;
    }
}
