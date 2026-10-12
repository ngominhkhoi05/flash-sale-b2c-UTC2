package com.b2c.flash_sale_b2c_UTC2.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO wrapper cho ảnh sản phẩm trong ProductDetailResponse.
 * Kế thừa ImageResponse và thêm trường alt (tên sản phẩm).
 * Được populate bằng cách gọi imageService.listImages(PRODUCT, productId).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageDto {

    private Long id;
    private String url;
    private Boolean isPrimary;
    private Integer displayOrder;
    private String alt;
}
