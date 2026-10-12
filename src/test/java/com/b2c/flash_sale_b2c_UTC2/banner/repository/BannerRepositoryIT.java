package com.b2c.flash_sale_b2c_UTC2.banner.repository;

import com.b2c.flash_sale_b2c_UTC2.banner.entity.Banner;
import com.b2c.flash_sale_b2c_UTC2.banner.enums.BannerType;
import com.b2c.flash_sale_b2c_UTC2.common.test.AbstractPostgresIT;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class BannerRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private BannerRepository bannerRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("findActiveBanners - Chi tra ve banner isActive=true va trong khoang thoi gian")
    void findActiveBanners_ReturnsOnlyActiveBannersWithinDateRange() {
        Instant now = Instant.now();

        Banner activeHero = Banner.builder()
                .type(BannerType.HERO)
                .position(1)
                .imageUrl("https://example.com/hero.jpg")
                .isActive(true)
                .startDate(now.minusSeconds(3600))
                .endDate(now.plusSeconds(3600))
                .build();
        em.persist(activeHero);

        Banner inactiveBanner = Banner.builder()
                .type(BannerType.FREESHIP)
                .position(1)
                .imageUrl("https://example.com/freeship.jpg")
                .isActive(false)
                .startDate(now.minusSeconds(3600))
                .endDate(now.plusSeconds(3600))
                .build();
        em.persist(inactiveBanner);

        Banner futureBanner = Banner.builder()
                .type(BannerType.PROMOTION)
                .position(1)
                .imageUrl("https://example.com/future.jpg")
                .isActive(true)
                .startDate(now.plusSeconds(3600))
                .endDate(now.plusSeconds(7200))
                .build();
        em.persist(futureBanner);

        Banner expiredBanner = Banner.builder()
                .type(BannerType.VOUCHER_NEW_SHOP)
                .position(1)
                .imageUrl("https://example.com/expired.jpg")
                .isActive(true)
                .startDate(now.minusSeconds(7200))
                .endDate(now.minusSeconds(3600))
                .build();
        em.persist(expiredBanner);

        em.flush();

        List<Banner> result = bannerRepository.findActiveBanners(now);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(BannerType.HERO);
        assertThat(result.get(0).getImageUrl()).isEqualTo("https://example.com/hero.jpg");
    }

    @Test
    @DisplayName("findActiveBanners - Tra ve empty khi khong co banner active")
    void findActiveBanners_ReturnsEmptyWhenNoActiveBanners() {
        List<Banner> result = bannerRepository.findActiveBanners(Instant.now());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findActiveBannersByType - Chi tra ve banner theo type")
    void findActiveBannersByType_ReturnsOnlyBannersOfSpecifiedType() {
        Instant now = Instant.now();

        Banner heroBanner = Banner.builder()
                .type(BannerType.HERO)
                .position(1)
                .imageUrl("https://example.com/hero.jpg")
                .isActive(true)
                .startDate(now.minusSeconds(3600))
                .endDate(now.plusSeconds(3600))
                .build();
        em.persist(heroBanner);

        Banner freeshipBanner = Banner.builder()
                .type(BannerType.FREESHIP)
                .position(1)
                .imageUrl("https://example.com/freeship.jpg")
                .isActive(true)
                .startDate(now.minusSeconds(3600))
                .endDate(now.plusSeconds(3600))
                .build();
        em.persist(freeshipBanner);

        em.flush();

        List<Banner> result = bannerRepository.findActiveBannersByType(BannerType.HERO, now);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(BannerType.HERO);
    }
}