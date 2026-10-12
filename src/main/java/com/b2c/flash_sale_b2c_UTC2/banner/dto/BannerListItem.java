package com.b2c.flash_sale_b2c_UTC2.banner.dto;

import com.b2c.flash_sale_b2c_UTC2.banner.enums.BannerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BannerListItem {
    private Long id;
    private BannerType type;
    private Integer position;
    private String imageUrl;
    private String imageAlt;
    private String ctaHref;
    private String ctaText;
    private Boolean isActive;
    private Instant startDate;
    private Instant endDate;
    private Instant createdAt;
}