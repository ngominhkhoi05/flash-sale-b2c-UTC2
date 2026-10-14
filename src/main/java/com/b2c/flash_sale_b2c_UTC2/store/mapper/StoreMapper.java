package com.b2c.flash_sale_b2c_UTC2.store.mapper;

import com.b2c.flash_sale_b2c_UTC2.store.dto.StoreResponse;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StoreMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(target = "logoUrl", ignore = true)
    StoreResponse toResponse(Store store);
}
