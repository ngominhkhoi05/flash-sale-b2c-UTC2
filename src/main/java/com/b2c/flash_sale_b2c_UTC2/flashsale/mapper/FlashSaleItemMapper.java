package com.b2c.flash_sale_b2c_UTC2.flashsale.mapper;

import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.FlashSaleItemResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FlashSaleItemMapper {

    @Mapping(source = "slot.id", target = "slotId")
    @Mapping(source = "variant.id", target = "variantId")
    @Mapping(source = "slot.title", target = "slotTitle")
    @Mapping(source = "slot.startTime", target = "slotStartTime")
    @Mapping(source = "slot.endTime", target = "slotEndTime")
    @Mapping(source = "slot.status", target = "slotStatus")
    @Mapping(source = "variant.product.store.id", target = "storeId")
    @Mapping(source = "variant.product.store.storeName", target = "storeName")
    @Mapping(source = "variant.product.id", target = "productId")
    @Mapping(source = "variant.product.name", target = "productName")
    @Mapping(source = "variant.sku", target = "skuCode")
    @Mapping(source = "variant.variantName", target = "variantName")
    @Mapping(source = "variant.originalPrice", target = "originalPrice")
    FlashSaleItemResponse toResponse(FlashSaleItem item);
}
