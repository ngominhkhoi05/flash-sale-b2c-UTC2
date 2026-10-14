package com.b2c.flash_sale_b2c_UTC2.image.dto;

import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO trả về thông tin một bản ghi ảnh từ bảng images.
 * Theo docs/api/api-document.md mục 28.4.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageResponse {
    private Long id;
    private ImageOwnerType ownerType;
    private Long ownerId;
    private String url;
    private String publicId;
    private Integer displayOrder;
    private Boolean isPrimary;
    private String status;
    private Instant createdAt;
}