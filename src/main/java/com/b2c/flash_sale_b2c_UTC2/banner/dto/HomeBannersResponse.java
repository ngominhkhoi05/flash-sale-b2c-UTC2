package com.b2c.flash_sale_b2c_UTC2.banner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeBannersResponse {
    private List<BannerResponse> mainSlides;
    private List<BannerResponse> sideBanners;
}
