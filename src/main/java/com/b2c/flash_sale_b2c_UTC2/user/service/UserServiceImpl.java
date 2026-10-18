package com.b2c.flash_sale_b2c_UTC2.user.service;

import com.b2c.flash_sale_b2c_UTC2.auth.exception.AuthErrorCode;
import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.user.dto.ChangePasswordRequest;
import com.b2c.flash_sale_b2c_UTC2.user.dto.UpdateProfileRequest;
import com.b2c.flash_sale_b2c_UTC2.user.dto.UserResponse;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.exception.UserErrorCode;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.user.mapper.UserMapper;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    private final ImageService imageService;
    private final com.b2c.flash_sale_b2c_UTC2.order.repository.OrderRepository orderRepository;
    private final com.b2c.flash_sale_b2c_UTC2.image.repository.ImageRepository imageRepository;

    private List<String> getUserRoles(Long userId) {
        if (userId == null || userRoleRepository == null) return List.of();
        var roles = userRoleRepository.findByUserId(userId);
        if (roles == null) return List.of();
        return roles.stream()
                .filter(ur -> ur != null && ur.getRole() != null && ur.getRole().getName() != null)
                .map(ur -> {
                    String name = ur.getRole().getName();
                    return name.startsWith("ROLE_") ? name : "ROLE_" + name;
                })
                .distinct()
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        UserResponse resp = userMapper.toResponse(user);
        com.b2c.flash_sale_b2c_UTC2.image.dto.ImageResponse avatar = imageService.getPrimaryImage(ImageOwnerType.USER, userId);
        if (avatar != null) {
            resp.setAvatarUrl(avatar.getUrl());
        }
        resp.setRoles(getUserRoles(userId));
        return resp;
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            String trimmedPhone = request.getPhone().trim();
            userRepository.findByPhone(trimmedPhone).ifPresent(existing -> {
                if (!existing.getId().equals(userId)) {
                    throw new BusinessException(AuthErrorCode.PHONE_ALREADY_EXISTS);
                }
            });
            user.setPhone(trimmedPhone);
        }

        user.setFullName(request.getFullName().trim());
        user.setUpdatedAt(Instant.now());

        user = userRepository.save(user);
        UserResponse resp = userMapper.toResponse(user);
        com.b2c.flash_sale_b2c_UTC2.image.dto.ImageResponse avatar = imageService.getPrimaryImage(ImageOwnerType.USER, userId);
        if (avatar != null) {
            resp.setAvatarUrl(avatar.getUrl());
        }
        resp.setRoles(getUserRoles(userId));
        return resp;
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException(UserErrorCode.OLD_PASSWORD_INCORRECT);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        // Thu hồi toàn bộ refresh token của user trong Redis
        revokeAllUserRefreshTokens(userId);
    }

    private void revokeAllUserRefreshTokens(Long userId) {
        try {
            String userTokensKey = "auth:user_tokens:" + userId;
            java.util.Set<String> jtis = stringRedisTemplate.opsForSet().members(userTokensKey);
            if (jtis != null && !jtis.isEmpty()) {
                java.util.List<String> keys = jtis.stream()
                        .map(jti -> "auth:refresh:" + userId + ":" + jti)
                        .toList();
                stringRedisTemplate.delete(keys);
            }
            stringRedisTemplate.delete(userTokensKey);
            log.info("Đã thu hồi toàn bộ Refresh Tokens trong Redis cho userId={}", userId);
        } catch (Exception ex) {
            log.warn("Lỗi khi thu hồi refresh tokens trong Redis cho userId={}: {}", userId, ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse<com.b2c.flash_sale_b2c_UTC2.user.dto.AdminUserResponse> getAdminUsers(
            String role,
            String status,
            String search,
            org.springframework.data.domain.Pageable pageable
    ) {
        String trimmedRole = (role != null && !role.trim().isEmpty() && !"ALL".equalsIgnoreCase(role.trim()))
                ? role.trim().toUpperCase()
                : null;
        String trimmedStatus = (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status.trim()))
                ? status.trim().toUpperCase()
                : null;
        String trimmedSearch = (search != null && !search.trim().isEmpty())
                ? search.trim()
                : null;

        var page = userRepository.findAdminUsers(trimmedRole, trimmedStatus, trimmedSearch, pageable);
        if (page.isEmpty()) {
            return com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse.of(page, java.util.Collections.emptyList());
        }

        List<Long> userIds = page.getContent().stream().map(User::getId).toList();

        // 1. Batch load roles for all users
        List<com.b2c.flash_sale_b2c_UTC2.user.entity.UserRole> userRoles = userRoleRepository.findByUserIdsWithRole(userIds);
        java.util.Map<Long, List<String>> userRolesMap = new java.util.HashMap<>();
        for (var ur : userRoles) {
            if (ur.getUser() != null && ur.getRole() != null) {
                String rName = ur.getRole().getName();
                String formattedRole = rName.startsWith("ROLE_") ? rName.substring(5) : rName;
                userRolesMap.computeIfAbsent(ur.getUser().getId(), k -> new java.util.ArrayList<>()).add(formattedRole);
            }
        }

        // 2. Batch load avatars
        List<com.b2c.flash_sale_b2c_UTC2.image.entity.Image> images = imageRepository.findByOwnerTypeAndOwnerIdIn(ImageOwnerType.USER, userIds);
        java.util.Map<Long, String> avatarMap = new java.util.HashMap<>();
        for (var img : images) {
            avatarMap.putIfAbsent(img.getOwnerId(), img.getUrl());
        }

        // 3. Batch load order counts
        List<Object[]> orderCounts = orderRepository.countOrdersByBuyerIds(userIds);
        java.util.Map<Long, Long> orderCountMap = new java.util.HashMap<>();
        for (Object[] row : orderCounts) {
            if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                orderCountMap.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            }
        }

        List<com.b2c.flash_sale_b2c_UTC2.user.dto.AdminUserResponse> content = page.getContent().stream().map(user -> {
            Long uid = user.getId();
            List<String> roles = userRolesMap.getOrDefault(uid, List.of("BUYER"));
            String primaryRole = roles.contains("ADMIN")
                    ? "ADMIN"
                    : (roles.contains("SELLER") ? "SELLER" : "BUYER");

            return com.b2c.flash_sale_b2c_UTC2.user.dto.AdminUserResponse.builder()
                    .id(uid)
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .phone(user.getPhone())
                    .avatarUrl(avatarMap.get(uid))
                    .status(user.getStatus())
                    .role(primaryRole)
                    .roles(roles)
                    .ordersCount(orderCountMap.getOrDefault(uid, 0L))
                    .createdAt(user.getCreatedAt())
                    .build();
        }).toList();

        return com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse.of(page, content);
    }

    @Override
    @Transactional
    public com.b2c.flash_sale_b2c_UTC2.user.dto.AdminUserResponse updateUserStatus(
            Long userId,
            com.b2c.flash_sale_b2c_UTC2.user.dto.UpdateUserStatusRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        String newStatus = request.getStatus().trim().toUpperCase();
        if (!"ACTIVE".equals(newStatus) && !"LOCKED".equals(newStatus)) {
            throw new BusinessException(UserErrorCode.INVALID_USER_STATUS);
        }

        // Kiểm tra nếu là ADMIN thì không được khóa
        List<String> roles = getUserRoles(userId);
        boolean isAdmin = roles.stream().anyMatch(r -> r.contains("ADMIN"));
        if (isAdmin && "LOCKED".equals(newStatus)) {
            throw new BusinessException(UserErrorCode.CANNOT_LOCK_ADMIN);
        }

        user.setStatus(newStatus);
        user = userRepository.save(user);

        if ("LOCKED".equals(newStatus)) {
            revokeAllUserRefreshTokens(userId);
        }

        log.info("Admin đã cập nhật trạng thái người dùng id={} thành {}, lý do: {}", userId, newStatus, request.getReason());

        String primaryRole = roles.stream().anyMatch(r -> r.contains("ADMIN"))
                ? "ADMIN"
                : (roles.stream().anyMatch(r -> r.contains("SELLER")) ? "SELLER" : "BUYER");

        var avatar = imageService.getPrimaryImage(ImageOwnerType.USER, userId);

        return com.b2c.flash_sale_b2c_UTC2.user.dto.AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .avatarUrl(avatar != null ? avatar.getUrl() : null)
                .status(user.getStatus())
                .role(primaryRole)
                .roles(roles)
                .ordersCount(0L)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
