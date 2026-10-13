package com.b2c.flash_sale_b2c_UTC2.banner.service;

import com.b2c.flash_sale_b2c_UTC2.banner.dto.BannerListItem;
import com.b2c.flash_sale_b2c_UTC2.banner.dto.BannerRequest;
import com.b2c.flash_sale_b2c_UTC2.banner.dto.HomeBannersResponse;
import com.b2c.flash_sale_b2c_UTC2.banner.enums.BannerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BannerService {

    // Public
    HomeBannersResponse getHomeBanners();

    List<BannerListItem> getActiveBannersByPosition(Integer position);

    // Admin
    BannerListItem createBanner(BannerRequest request);

    BannerListItem updateBanner(Long id, BannerRequest request);

    void deleteBanner(Long id);

    BannerListItem changeStatus(Long id, Boolean isActive);

    Page<BannerListItem> listBanners(String type, Boolean isActive, Pageable pageable);
}