package com.b2c.flash_sale_b2c_UTC2.image.mapper;

import com.b2c.flash_sale_b2c_UTC2.image.dto.ImageResponse;
import com.b2c.flash_sale_b2c_UTC2.image.entity.Image;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper giữa Entity Image và DTO ImageResponse.
 */
@Mapper(componentModel = "spring")
public interface ImageMapper {

    @Mapping(source = "ownerType", target = "ownerType")
    @Mapping(source = "cloudinaryPublicId", target = "publicId")
    ImageResponse toResponse(Image image);
}