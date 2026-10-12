package com.b2c.flash_sale_b2c_UTC2.banner.mapper;

import com.b2c.flash_sale_b2c_UTC2.banner.dto.BannerListItem;
import com.b2c.flash_sale_b2c_UTC2.banner.dto.BannerRequest;
import com.b2c.flash_sale_b2c_UTC2.banner.dto.BannerResponse;
import com.b2c.flash_sale_b2c_UTC2.banner.entity.Banner;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface BannerMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Banner toEntity(BannerRequest request);

    BannerResponse toResponse(Banner entity);

    BannerListItem toListItem(Banner entity);

    List<BannerListItem> toListItemList(List<Banner> entities);
}