package com.b2c.flash_sale_b2c_UTC2.user.service;

import com.b2c.flash_sale_b2c_UTC2.user.dto.ChangePasswordRequest;
import com.b2c.flash_sale_b2c_UTC2.user.dto.UpdateProfileRequest;
import com.b2c.flash_sale_b2c_UTC2.user.dto.UserResponse;

public interface UserService {

    UserResponse getProfile(Long userId);

    UserResponse updateProfile(Long userId, UpdateProfileRequest request);

    void changePassword(Long userId, ChangePasswordRequest request);

    com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse<com.b2c.flash_sale_b2c_UTC2.user.dto.AdminUserResponse> getAdminUsers(
            String role,
            String status,
            String search,
            org.springframework.data.domain.Pageable pageable
    );

    com.b2c.flash_sale_b2c_UTC2.user.dto.AdminUserResponse updateUserStatus(
            Long userId,
            com.b2c.flash_sale_b2c_UTC2.user.dto.UpdateUserStatusRequest request
    );
}
