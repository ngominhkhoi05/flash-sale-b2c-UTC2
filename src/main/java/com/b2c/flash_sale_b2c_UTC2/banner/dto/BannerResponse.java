package com.b2c.flash_sale_b2c_UTC2.banner.dto;

import com.b2c.flash_sale_b2c_UTC2.banner.enums.BannerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BannerResponse {
    private Long id;
    private BannerType type;
    private String imageUrl;
    private String imageAlt;
    private String ctaHref;
    private String ctaText;
}
