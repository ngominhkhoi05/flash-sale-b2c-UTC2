package com.b2c.flash_sale_b2c_UTC2.banner.service;

import com.b2c.flash_sale_b2c_UTC2.banner.dto.BannerListItem;
import com.b2c.flash_sale_b2c_UTC2.banner.dto.BannerRequest;
import com.b2c.flash_sale_b2c_UTC2.banner.dto.BannerResponse;
import com.b2c.flash_sale_b2c_UTC2.banner.dto.HomeBannersResponse;
import com.b2c.flash_sale_b2c_UTC2.banner.entity.Banner;
import com.b2c.flash_sale_b2c_UTC2.banner.enums.BannerType;
import com.b2c.flash_sale_b2c_UTC2.banner.exception.BannerErrorCode;
import com.b2c.flash_sale_b2c_UTC2.banner.mapper.BannerMapper;
import com.b2c.flash_sale_b2c_UTC2.banner.repository.BannerRepository;
import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private final BannerRepository bannerRepository;
    private final BannerMapper bannerMapper;

    // ========================================================================
    // Public
    // ========================================================================

    @Override
    @Transactional(readOnly = true)
    public HomeBannersResponse getHomeBanners() {
        Instant now = Instant.now();
        List<Banner> activeBanners = bannerRepository.findActiveBanners(now);

        List<BannerResponse> mainSlides = activeBanners.stream()
                .filter(b -> b.getType() == BannerType.HERO)
                .sorted(Comparator.comparingInt(Banner::getPosition))
                .map(bannerMapper::toResponse)
                .collect(Collectors.toList());

        List<BannerResponse> sideBanners = activeBanners.stream()
                .filter(b -> b.getType() != BannerType.HERO)
                .sorted(Comparator.comparingInt(Banner::getPosition))
                .map(bannerMapper::toResponse)
                .collect(Collectors.toList());

        log.debug("Loaded {} main slides and {} side banners", mainSlides.size(), sideBanners.size());

        return HomeBannersResponse.builder()
                .mainSlides(mainSlides)
                .sideBanners(sideBanners)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BannerListItem> getActiveBannersByPosition(Integer position) {
        Instant now = Instant.now();
        List<Banner> banners = bannerRepository.findActiveByPosition(position, now);
        return bannerMapper.toListItemList(banners);
    }

    // ========================================================================
    // Admin
    // ========================================================================

    @Override
    @Transactional
    public BannerListItem createBanner(BannerRequest request) {
        validateBannerRequest(request);

        Banner banner = Banner.builder()
                .type(request.getType())
                .position(request.getPosition() != null ? request.getPosition() : 0)
                .imageUrl(request.getImageUrl())
                .imageAlt(request.getImageAlt())
                .ctaHref(request.getCtaHref())
                .ctaText(request.getCtaText())
                .isActive(request.getIsActive() != null ? request.getIsActive() : false)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .createdAt(Instant.now())
                .build();

        Banner saved = bannerRepository.save(banner);
        log.info("Banner created: id={}, type={}", saved.getId(), saved.getType());
        return bannerMapper.toListItem(saved);
    }

    @Override
    @Transactional
    public BannerListItem updateBanner(Long id, BannerRequest request) {
        validateBannerRequest(request);

        Banner existing = bannerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BannerErrorCode.BANNER_NOT_FOUND));

        Banner updated = Banner.builder()
                .id(existing.getId())
                .type(request.getType())
                .position(request.getPosition() != null ? request.getPosition() : existing.getPosition())
                .imageUrl(request.getImageUrl())
                .imageAlt(request.getImageAlt())
                .ctaHref(request.getCtaHref())
                .ctaText(request.getCtaText())
                .isActive(request.getIsActive() != null ? request.getIsActive() : existing.getIsActive())
                .startDate(request.getStartDate() != null ? request.getStartDate() : existing.getStartDate())
                .endDate(request.getEndDate() != null ? request.getEndDate() : existing.getEndDate())
                .createdAt(existing.getCreatedAt())
                .build();

        Banner saved = bannerRepository.save(updated);
        log.info("Banner updated: id={}", saved.getId());
        return bannerMapper.toListItem(saved);
    }

    @Override
    @Transactional
    public void deleteBanner(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BannerErrorCode.BANNER_NOT_FOUND));

        // Soft delete: set isActive = false
        Banner deactivated = Banner.builder()
                .id(banner.getId())
                .type(banner.getType())
                .position(banner.getPosition())
                .imageUrl(banner.getImageUrl())
                .imageAlt(banner.getImageAlt())
                .ctaHref(banner.getCtaHref())
                .ctaText(banner.getCtaText())
                .isActive(false)
                .startDate(banner.getStartDate())
                .endDate(banner.getEndDate())
                .createdAt(banner.getCreatedAt())
                .build();
        bannerRepository.save(deactivated);
        log.info("Banner soft-deleted (deactivated): id={}", id);
    }

    @Override
    @Transactional
    public BannerListItem changeStatus(Long id, Boolean isActive) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BannerErrorCode.BANNER_NOT_FOUND));

        // Validate: INACTIVE -> ACTIVE requires time window to be valid
        if (Boolean.TRUE.equals(isActive) && !Boolean.TRUE.equals(banner.getIsActive())) {
            validateTimeWindow(banner, id);
        }

        Banner updated = Banner.builder()
                .id(banner.getId())
                .type(banner.getType())
                .position(banner.getPosition())
                .imageUrl(banner.getImageUrl())
                .imageAlt(banner.getImageAlt())
                .ctaHref(banner.getCtaHref())
                .ctaText(banner.getCtaText())
                .isActive(isActive)
                .startDate(banner.getStartDate())
                .endDate(banner.getEndDate())
                .createdAt(banner.getCreatedAt())
                .build();

        Banner saved = bannerRepository.save(updated);
        log.info("Banner status changed: id={}, isActive={}", saved.getId(), saved.getIsActive());
        return bannerMapper.toListItem(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BannerListItem> listBanners(String type, Boolean isActive, Pageable pageable) {
        Page<Banner> page = bannerRepository.findAll(pageable);
        List<Banner> filtered = page.getContent();

        if (type != null) {
            BannerType bannerType = BannerType.valueOf(type.toUpperCase());
            filtered = filtered.stream()
                    .filter(b -> b.getType() == bannerType)
                    .collect(Collectors.toList());
        }
        if (isActive != null) {
            filtered = filtered.stream()
                    .filter(b -> Objects.equals(b.getIsActive(), isActive))
                    .collect(Collectors.toList());
        }

        List<BannerListItem> items = filtered.stream()
                .map(bannerMapper::toListItem)
                .collect(Collectors.toList());
        return new PageImpl<>(items, pageable, page.getTotalElements());
    }

    // ========================================================================
    // Validation helpers
    // ========================================================================

    private void validateBannerRequest(BannerRequest request) {
        if (request.getImageUrl() == null || request.getImageUrl().isBlank()) {
            throw new BusinessException(BannerErrorCode.BANNER_IMAGE_URL_BLANK);
        }
        if (request.getStartDate() != null && request.getEndDate() != null
                && request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessException(BannerErrorCode.BANNER_INVALID_DATE_RANGE);
        }
    }

    private void validateTimeWindow(Banner banner, Long id) {
        Instant now = Instant.now();
        // If startDate is in the future, banner cannot be activated yet
        if (banner.getStartDate() != null && banner.getStartDate().isAfter(now)) {
            throw new BusinessException(BannerErrorCode.BANNER_INVALID_STATUS);
        }
        // If endDate has already passed, banner cannot be activated
        if (banner.getEndDate() != null && banner.getEndDate().isBefore(now)) {
            throw new BusinessException(BannerErrorCode.BANNER_INVALID_STATUS);
        }
    }
}