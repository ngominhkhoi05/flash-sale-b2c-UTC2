package com.b2c.flash_sale_b2c_UTC2.banner.service;

import com.b2c.flash_sale_b2c_UTC2.banner.dto.BannerListItem;
import com.b2c.flash_sale_b2c_UTC2.banner.dto.BannerRequest;
import com.b2c.flash_sale_b2c_UTC2.banner.entity.Banner;
import com.b2c.flash_sale_b2c_UTC2.banner.enums.BannerType;
import com.b2c.flash_sale_b2c_UTC2.banner.exception.BannerErrorCode;
import com.b2c.flash_sale_b2c_UTC2.banner.mapper.BannerMapper;
import com.b2c.flash_sale_b2c_UTC2.banner.mapper.BannerMapperImpl;
import com.b2c.flash_sale_b2c_UTC2.banner.repository.BannerRepository;
import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BannerAdminServiceTest {

    @Mock private BannerRepository bannerRepository;
    @Spy private BannerMapper bannerMapper = new BannerMapperImpl();
    @InjectMocks private BannerServiceImpl bannerService;

    private Banner activeHeroBanner;
    private Banner inactiveBanner;
    private BannerRequest validRequest;

    @BeforeEach
    void setUp() {
        activeHeroBanner = Banner.builder()
                .id(1L).type(BannerType.HERO).position(1)
                .imageUrl("https://example.com/hero.jpg").imageAlt("Hero alt")
                .ctaHref("/promo").ctaText("Mua ngay")
                .isActive(true)
                .startDate(Instant.now().minusSeconds(3600))
                .endDate(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .build();

        inactiveBanner = Banner.builder()
                .id(2L).type(BannerType.PROMOTION).position(2)
                .imageUrl("https://example.com/promo.jpg").imageAlt("Promo alt")
                .ctaHref("/promo2").ctaText("Xem ngay")
                .isActive(false)
                .startDate(Instant.now().minusSeconds(7200))
                .endDate(Instant.now().plusSeconds(7200))
                .createdAt(Instant.now())
                .build();

        validRequest = BannerRequest.builder()
                .type(BannerType.HERO)
                .position(1)
                .imageUrl("https://example.com/new.jpg")
                .imageAlt("New banner")
                .ctaHref("/new")
                .ctaText("New CTA")
                .isActive(true)
                .startDate(Instant.now().minusSeconds(3600))
                .endDate(Instant.now().plusSeconds(86400))
                .build();
    }

    // ==============================================================
    // createBanner tests
    // ==============================================================

    @Nested
    @DisplayName("createBanner")
    class CreateBannerTests {

        @Test
        @DisplayName("Happy: create banner with valid request -> returns BannerListItem with id")
        void createBanner_ValidRequest_ReturnsCreatedItem() {
            Banner savedBanner = Banner.builder()
                    .id(10L)
                    .type(BannerType.HERO)
                    .position(1)
                    .imageUrl("https://example.com/new.jpg")
                    .imageAlt("New banner")
                    .ctaHref("/new")
                    .ctaText("New CTA")
                    .isActive(true)
                    .startDate(Instant.now().minusSeconds(3600))
                    .endDate(Instant.now().plusSeconds(86400))
                    .createdAt(Instant.now())
                    .build();
            when(bannerRepository.save(any(Banner.class))).thenReturn(savedBanner);

            BannerListItem result = bannerService.createBanner(validRequest);

            assertNotNull(result);
            assertEquals(10L, result.getId());
            assertEquals(BannerType.HERO, result.getType());
            assertEquals("https://example.com/new.jpg", result.getImageUrl());
            assertTrue(result.getIsActive());

            ArgumentCaptor<Banner> captor = ArgumentCaptor.forClass(Banner.class);
            verify(bannerRepository).save(captor.capture());
            assertEquals(BannerType.HERO, captor.getValue().getType());
            assertEquals("https://example.com/new.jpg", captor.getValue().getImageUrl());
        }

        @Test
        @DisplayName("Edge: create banner with blank imageUrl -> throws BANNER_IMAGE_URL_BLANK (400)")
        void createBanner_BlankImageUrl_ThrowsBannerErrorCode() {
            BannerRequest badRequest = BannerRequest.builder()
                    .type(BannerType.HERO)
                    .imageUrl("   ")
                    .build();

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> bannerService.createBanner(badRequest));
            assertEquals(BannerErrorCode.BANNER_IMAGE_URL_BLANK, ex.getErrorCode());
        }

        @Test
        @DisplayName("Edge: create banner with startDate after endDate -> throws BANNER_INVALID_DATE_RANGE")
        void createBanner_InvalidDateRange_ThrowsBannerErrorCode() {
            BannerRequest badRequest = BannerRequest.builder()
                    .type(BannerType.FREESHIP)
                    .imageUrl("https://example.com/freeship.jpg")
                    .startDate(Instant.now().plusSeconds(86400))
                    .endDate(Instant.now())
                    .build();

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> bannerService.createBanner(badRequest));
            assertEquals(BannerErrorCode.BANNER_INVALID_DATE_RANGE, ex.getErrorCode());
        }
    }

    // ==============================================================
    // changeStatus tests
    // ==============================================================

    @Nested
    @DisplayName("changeStatus")
    class ChangeStatusTests {

        @Test
        @DisplayName("Happy: deactivate active banner -> isActive=false")
        void changeStatus_DeactivateActive_Success() {
            when(bannerRepository.findById(1L)).thenReturn(Optional.of(activeHeroBanner));
            when(bannerRepository.save(any(Banner.class))).thenAnswer(i -> i.getArgument(0));

            BannerListItem result = bannerService.changeStatus(1L, false);

            assertNotNull(result);
            assertFalse(result.getIsActive());
        }

        @Test
        @DisplayName("Happy: activate inactive banner within time window -> isActive=true")
        void changeStatus_ActivateInactiveWithinWindow_Success() {
            when(bannerRepository.findById(2L)).thenReturn(Optional.of(inactiveBanner));
            when(bannerRepository.save(any(Banner.class))).thenAnswer(i -> i.getArgument(0));

            BannerListItem result = bannerService.changeStatus(2L, true);

            assertNotNull(result);
            assertTrue(result.getIsActive());
        }

        @Test
        @DisplayName("Edge: activate banner with startDate in future -> throws BANNER_INVALID_STATUS (422)")
        void changeStatus_ActivateWithFutureStartDate_ThrowsInvalidStatus() {
            Banner futureBanner = Banner.builder()
                    .id(3L).type(BannerType.VOUCHER_NEW_SHOP).position(1)
                    .imageUrl("https://example.com/future.jpg").imageAlt("Future")
                    .isActive(false)
                    .startDate(Instant.now().plusSeconds(86400))
                    .endDate(Instant.now().plusSeconds(172800))
                    .createdAt(Instant.now())
                    .build();

            when(bannerRepository.findById(3L)).thenReturn(Optional.of(futureBanner));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> bannerService.changeStatus(3L, true));
            assertEquals(BannerErrorCode.BANNER_INVALID_STATUS, ex.getErrorCode());
        }

        @Test
        @DisplayName("Edge: activate banner with endDate in past -> throws BANNER_INVALID_STATUS (422)")
        void changeStatus_ActivateWithPastEndDate_ThrowsInvalidStatus() {
            Banner expiredBanner = Banner.builder()
                    .id(4L).type(BannerType.PROMOTION).position(1)
                    .imageUrl("https://example.com/expired.jpg").imageAlt("Expired")
                    .isActive(false)
                    .startDate(Instant.now().minusSeconds(172800))
                    .endDate(Instant.now().minusSeconds(3600))
                    .createdAt(Instant.now().minusSeconds(172800))
                    .build();

            when(bannerRepository.findById(4L)).thenReturn(Optional.of(expiredBanner));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> bannerService.changeStatus(4L, true));
            assertEquals(BannerErrorCode.BANNER_INVALID_STATUS, ex.getErrorCode());
        }

        @Test
        @DisplayName("Edge: change status of non-existent banner -> throws BANNER_NOT_FOUND (404)")
        void changeStatus_BannerNotFound_ThrowsNotFound() {
            when(bannerRepository.findById(999L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> bannerService.changeStatus(999L, true));
            assertEquals(BannerErrorCode.BANNER_NOT_FOUND, ex.getErrorCode());
        }
    }

    // ==============================================================
    // deleteBanner tests (soft delete)
    // ==============================================================

    @Nested
    @DisplayName("deleteBanner")
    class DeleteBannerTests {

        @Test
        @DisplayName("Happy: delete existing banner -> isActive set to false")
        void deleteBanner_ExistingBanner_SoftDeletes() {
            when(bannerRepository.findById(1L)).thenReturn(Optional.of(activeHeroBanner));
            when(bannerRepository.save(any(Banner.class))).thenAnswer(i -> i.getArgument(0));

            bannerService.deleteBanner(1L);

            ArgumentCaptor<Banner> captor = ArgumentCaptor.forClass(Banner.class);
            verify(bannerRepository).save(captor.capture());
            assertFalse(captor.getValue().getIsActive());
            assertEquals(1L, captor.getValue().getId());
        }

        @Test
        @DisplayName("Edge: delete non-existent banner -> throws BANNER_NOT_FOUND (404)")
        void deleteBanner_NotFound_ThrowsNotFound() {
            when(bannerRepository.findById(999L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> bannerService.deleteBanner(999L));
            assertEquals(BannerErrorCode.BANNER_NOT_FOUND, ex.getErrorCode());
        }
    }

    // ==============================================================
    // getActiveBannersByPosition tests
    // ==============================================================

    @Nested
    @DisplayName("getActiveBannersByPosition")
    class GetActiveBannersByPositionTests {

        @Test
        @DisplayName("Happy: get active banners by position -> returns sorted list")
        void getActiveBannersByPosition_ReturnsSortedList() {
            Banner b1 = Banner.builder().id(1L).type(BannerType.HERO).position(1)
                    .imageUrl("url1").isActive(true)
                    .startDate(Instant.now().minusSeconds(3600)).endDate(Instant.now().plusSeconds(3600))
                    .createdAt(Instant.now()).build();
            Banner b2 = Banner.builder().id(2L).type(BannerType.HERO).position(1)
                    .imageUrl("url2").isActive(true)
                    .startDate(Instant.now().minusSeconds(3600)).endDate(Instant.now().plusSeconds(3600))
                    .createdAt(Instant.now()).build();

            when(bannerRepository.findActiveByPosition(anyInt(), any(Instant.class))).thenReturn(List.of(b1, b2));

            List<BannerListItem> result = bannerService.getActiveBannersByPosition(1);

            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals(BannerType.HERO, result.get(0).getType());
            assertEquals(1, result.get(0).getPosition());
        }

        @Test
        @DisplayName("Edge: no banners at position -> returns empty list")
        void getActiveBannersByPosition_NoBanners_ReturnsEmptyList() {
            when(bannerRepository.findActiveByPosition(anyInt(), any(Instant.class))).thenReturn(List.of());

            List<BannerListItem> result = bannerService.getActiveBannersByPosition(99);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }
}
