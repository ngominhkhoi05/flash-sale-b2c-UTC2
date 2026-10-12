package com.b2c.flash_sale_b2c_UTC2.banner.service;

import com.b2c.flash_sale_b2c_UTC2.banner.dto.BannerResponse;
import com.b2c.flash_sale_b2c_UTC2.banner.dto.HomeBannersResponse;
import com.b2c.flash_sale_b2c_UTC2.banner.entity.Banner;
import com.b2c.flash_sale_b2c_UTC2.banner.enums.BannerType;
import com.b2c.flash_sale_b2c_UTC2.banner.repository.BannerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BannerServiceImplTest {

    @Mock
    private BannerRepository bannerRepository;

    @InjectMocks
    private BannerServiceImpl bannerService;

    private Banner heroBanner1;
    private Banner heroBanner2;
    private Banner freeshipBanner;
    private Banner voucherBanner;

    @BeforeEach
    void setUp() {
        heroBanner1 = Banner.builder()
                .id(1L)
                .type(BannerType.HERO)
                .position(1)
                .imageUrl("https://example.com/hero1.jpg")
                .imageAlt("Hero 1")
                .ctaHref("/promo/1")
                .ctaText("Mua ngay")
                .isActive(true)
                .startDate(Instant.now().minusSeconds(3600))
                .endDate(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .build();

        heroBanner2 = Banner.builder()
                .id(2L)
                .type(BannerType.HERO)
                .position(2)
                .imageUrl("https://example.com/hero2.jpg")
                .imageAlt("Hero 2")
                .ctaHref("/promo/2")
                .ctaText("Xem ngay")
                .isActive(true)
                .startDate(Instant.now().minusSeconds(3600))
                .endDate(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .build();

        freeshipBanner = Banner.builder()
                .id(3L)
                .type(BannerType.FREESHIP)
                .position(1)
                .imageUrl("https://example.com/freeship.jpg")
                .imageAlt("Freeship")
                .ctaHref("/freeship")
                .ctaText("Áp dụng ngay")
                .isActive(true)
                .startDate(Instant.now().minusSeconds(3600))
                .endDate(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .build();

        voucherBanner = Banner.builder()
                .id(4L)
                .type(BannerType.VOUCHER_NEW_SHOP)
                .position(2)
                .imageUrl("https://example.com/voucher.jpg")
                .imageAlt("Voucher")
                .ctaHref("/voucher")
                .ctaText("Nhận voucher")
                .isActive(true)
                .startDate(Instant.now().minusSeconds(3600))
                .endDate(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("getHomeBanners - Happy path: phan tach HERO va non-HERO thanh cong")
    void getHomeBanners_Success_SeparatesHeroAndSideBanners() {
        List<Banner> banners = List.of(heroBanner1, heroBanner2, freeshipBanner, voucherBanner);
        when(bannerRepository.findActiveBanners(any(Instant.class))).thenReturn(banners);

        HomeBannersResponse response = bannerService.getHomeBanners();

        assertNotNull(response);
        assertEquals(2, response.getMainSlides().size());
        assertEquals(2, response.getSideBanners().size());

        // Hero banners sorted by position
        assertEquals(BannerType.HERO, response.getMainSlides().get(0).getType());
        assertEquals(BannerType.HERO, response.getMainSlides().get(1).getType());
        assertEquals(1L, response.getMainSlides().get(0).getId());
        assertEquals(2L, response.getMainSlides().get(1).getId());

        // Side banners sorted by position
        assertEquals(BannerType.FREESHIP, response.getSideBanners().get(0).getType());
        assertEquals(BannerType.VOUCHER_NEW_SHOP, response.getSideBanners().get(1).getType());

        // Verify BannerResponse mapping
        BannerResponse hero = response.getMainSlides().get(0);
        assertEquals("https://example.com/hero1.jpg", hero.getImageUrl());
        assertEquals("Hero 1", hero.getImageAlt());
        assertEquals("/promo/1", hero.getCtaHref());
        assertEquals("Mua ngay", hero.getCtaText());

        verify(bannerRepository).findActiveBanners(any(Instant.class));
    }

    @Test
    @DisplayName("getHomeBanners - Empty result: tra ve empty lists")
    void getHomeBanners_EmptyResult_ReturnsEmptyLists() {
        when(bannerRepository.findActiveBanners(any(Instant.class))).thenReturn(Collections.emptyList());

        HomeBannersResponse response = bannerService.getHomeBanners();

        assertNotNull(response);
        assertNotNull(response.getMainSlides());
        assertNotNull(response.getSideBanners());
        assertTrue(response.getMainSlides().isEmpty());
        assertTrue(response.getSideBanners().isEmpty());
        verify(bannerRepository).findActiveBanners(any(Instant.class));
    }

    @Test
    @DisplayName("getHomeBanners - Only HERO banners: sideBanners empty")
    void getHomeBanners_OnlyHeroBanners_SideBannersEmpty() {
        when(bannerRepository.findActiveBanners(any(Instant.class))).thenReturn(List.of(heroBanner1, heroBanner2));

        HomeBannersResponse response = bannerService.getHomeBanners();

        assertNotNull(response);
        assertEquals(2, response.getMainSlides().size());
        assertTrue(response.getSideBanners().isEmpty());
    }

    @Test
    @DisplayName("getHomeBanners - Only non-HERO banners: mainSlides empty")
    void getHomeBanners_OnlyNonHeroBanners_MainSlidesEmpty() {
        when(bannerRepository.findActiveBanners(any(Instant.class))).thenReturn(List.of(freeshipBanner, voucherBanner));

        HomeBannersResponse response = bannerService.getHomeBanners();

        assertNotNull(response);
        assertTrue(response.getMainSlides().isEmpty());
        assertEquals(2, response.getSideBanners().size());
    }
}
