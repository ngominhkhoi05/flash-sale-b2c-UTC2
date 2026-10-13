package com.b2c.flash_sale_b2c_UTC2.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerReplyDto {
    private String content;
    private String storeName;
    private Instant repliedAt;
}
