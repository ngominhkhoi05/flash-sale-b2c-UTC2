package com.b2c.flash_sale_b2c_UTC2.user.mapper;

import com.b2c.flash_sale_b2c_UTC2.user.dto.UserResponse;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(target = "avatarUrl", ignore = true)
    UserResponse toResponse(User user);
}
