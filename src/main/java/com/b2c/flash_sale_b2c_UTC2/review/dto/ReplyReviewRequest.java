package com.b2c.flash_sale_b2c_UTC2.review.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplyReviewRequest {

    @NotBlank(message = "Nội dung phản hồi không được để trống")
    private String replyText;
}
