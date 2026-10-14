package com.b2c.flash_sale_b2c_UTC2.user.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.common.exception.CommonErrorCode;
import com.b2c.flash_sale_b2c_UTC2.image.dto.ImageResponse;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.user.dto.ChangePasswordRequest;
import com.b2c.flash_sale_b2c_UTC2.user.dto.UpdateProfileRequest;
import com.b2c.flash_sale_b2c_UTC2.user.dto.UserResponse;
import com.b2c.flash_sale_b2c_UTC2.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "User Profile", description = "Quản lý hồ sơ cá nhân và đổi mật khẩu người dùng")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ImageService imageService;

    @Operation(summary = "Xem hồ sơ cá nhân", description = "Lấy thông tin người dùng hiện tại đang đăng nhập")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long currentUserId = validateAndGetUserId(userDetails);
        UserResponse response = userService.getProfile(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Cập nhật hồ sơ cá nhân", description = "Cập nhật họ tên, số điện thoại hoặc ảnh đại diện")
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        Long currentUserId = validateAndGetUserId(userDetails);
        UserResponse response = userService.updateProfile(currentUserId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin thành công", response));
    }

    @Operation(summary = "Đổi mật khẩu", description = "Xác nhận mật khẩu cũ và thiết lập mật khẩu mới")
    @PutMapping("/me/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        Long currentUserId = validateAndGetUserId(userDetails);
        userService.changePassword(currentUserId, request);
        return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công", null));
    }

    private Long validateAndGetUserId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getId() == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return userDetails.getId();
    }

    @Operation(summary = "Upload avatar hiện tại", description = "Tự động soft-delete avatar cũ (partial unique index đảm bảo 1 user chỉ có 1 avatar ACTIVE).")
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImageResponse>> uploadAvatar(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestPart("file") MultipartFile file
    ) {
        Long currentUserId = validateAndGetUserId(userDetails);
        ImageResponse resp = imageService.attachImage(
                currentUserId, ImageOwnerType.USER, currentUserId, file, null, null);
        return ResponseEntity.status(HttpStatusCode.valueOf(201))
                .body(ApiResponse.success("Upload avatar thành công", resp));
    }
}
