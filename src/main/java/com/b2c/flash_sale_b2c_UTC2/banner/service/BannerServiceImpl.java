package com.b2c.flash_sale_b2c_UTC2.banner.service;

import com.b2c.flash_sale_b2c_UTC2.banner.dto.BannerResponse;
import com.b2c.flash_sale_b2c_UTC2.banner.dto.HomeBannersResponse;
import com.b2c.flash_sale_b2c_UTC2.banner.entity.Banner;
import com.b2c.flash_sale_b2c_UTC2.banner.enums.BannerType;
import com.b2c.flash_sale_b2c_UTC2.banner.repository.BannerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private final BannerRepository bannerRepository;

    @Override
    @Transactional(readOnly = true)
    public HomeBannersResponse getHomeBanners() {
        Instant now = Instant.now();
        List<Banner> activeBanners = bannerRepository.findActiveBanners(now);

        List<BannerResponse> mainSlides = activeBanners.stream()
                .filter(b -> b.getType() == BannerType.HERO)
                .sorted(Comparator.comparingInt(Banner::getPosition))
                .map(this::toResponse)
                .collect(Collectors.toList());

        List<BannerResponse> sideBanners = activeBanners.stream()
                .filter(b -> b.getType() != BannerType.HERO)
                .sorted(Comparator.comparingInt(Banner::getPosition))
                .map(this::toResponse)
                .collect(Collectors.toList());

        log.debug("Loaded {} main slides and {} side banners", mainSlides.size(), sideBanners.size());

        return HomeBannersResponse.builder()
                .mainSlides(mainSlides)
                .sideBanners(sideBanners)
                .build();
    }

    private BannerResponse toResponse(Banner banner) {
        return BannerResponse.builder()
                .id(banner.getId())
                .type(banner.getType())
                .imageUrl(banner.getImageUrl())
                .imageAlt(banner.getImageAlt())
                .ctaHref(banner.getCtaHref())
                .ctaText(banner.getCtaText())
                .build();
    }
}
