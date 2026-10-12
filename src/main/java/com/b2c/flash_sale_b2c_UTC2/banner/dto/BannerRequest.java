package com.b2c.flash_sale_b2c_UTC2.banner.dto;

import com.b2c.flash_sale_b2c_UTC2.banner.enums.BannerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BannerRequest {

    @NotNull(message = "Loai banner khong duoc de trong")
    private BannerType type;

    @PositiveOrZero(message = "Vi tri hien thi phai >= 0")
    private Integer position;

    @NotBlank(message = "URL hinh anh khong duoc de trong")
    @Size(max = 500, message = "URL hinh anh toi da 500 ky tu")
    private String imageUrl;

    @Size(max = 255, message = "Alt text toi da 255 ky tu")
    private String imageAlt;

    @Size(max = 255, message = "CTA link toi da 255 ky tu")
    private String ctaHref;

    @Size(max = 50, message = "CTA text toi da 50 ky tu")
    private String ctaText;

    private Boolean isActive;

    private Instant startDate;

    private Instant endDate;
}