package com.b2c.flash_sale_b2c_UTC2.permission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportedReviewResponse {
    private String id;
    private String reviewerName;
    private String reviewerId;
    private String reviewerInitials;
    private int rating;
    private String productName;
    private String productThumbnail;
    private String content;
    private String reportReason;
    private int reportCount;
    private String reportedAt;
    private String status; // PENDING_REVIEW, HIDDEN, RESTORED
    private List<String> reviewImages;
}
