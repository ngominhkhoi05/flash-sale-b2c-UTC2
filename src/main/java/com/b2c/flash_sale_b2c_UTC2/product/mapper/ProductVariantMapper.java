package com.b2c.flash_sale_b2c_UTC2.product.mapper;

import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductVariantResponse;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductVariantMapper {

    @Mapping(source = "product.id", target = "productId")
    @Mapping(target = "imageUrl", ignore = true)
    ProductVariantResponse toResponse(ProductVariant variant);
}
