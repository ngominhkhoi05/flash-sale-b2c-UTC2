package com.b2c.flash_sale_b2c_UTC2.auth.service;

import com.b2c.flash_sale_b2c_UTC2.auth.dto.AuthResponse;
import com.b2c.flash_sale_b2c_UTC2.auth.dto.LoginRequest;
import com.b2c.flash_sale_b2c_UTC2.auth.dto.RefreshTokenRequest;
import com.b2c.flash_sale_b2c_UTC2.auth.dto.RegisterRequest;
import com.b2c.flash_sale_b2c_UTC2.auth.exception.AuthErrorCode;
import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.user.dto.UserResponse;
import com.b2c.flash_sale_b2c_UTC2.user.entity.Role;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.entity.UserRole;
import com.b2c.flash_sale_b2c_UTC2.user.mapper.UserMapper;
import com.b2c.flash_sale_b2c_UTC2.user.repository.RoleRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final UserMapper userMapper;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String REDIS_REFRESH_KEY_PREFIX = "auth:refresh:";
    private static final String REDIS_USER_TOKENS_PREFIX = "auth:user_tokens:";

    private List<String> getUserRoles(Long userId) {
        if (userId == null || userRoleRepository == null) return List.of();
        List<UserRole> list = userRoleRepository.findByUserId(userId);
        if (list == null || list.isEmpty()) return List.of();
        return list.stream()
                .filter(ur -> ur != null && ur.getRole() != null && ur.getRole().getName() != null)
                .map(ur -> {
                    String name = ur.getRole().getName();
                    return name.startsWith("ROLE_") ? name : "ROLE_" + name;
                })
                .distinct()
                .toList();
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            String trimmedPhone = request.getPhone().trim();
            if (userRepository.existsByPhone(trimmedPhone)) {
                throw new BusinessException(AuthErrorCode.PHONE_ALREADY_EXISTS);
            }
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .phone(request.getPhone() != null && !request.getPhone().isBlank() ? request.getPhone().trim() : null)
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        user = userRepository.save(user);

        // Phân quyền vai trò ban đầu (mặc định BUYER)
        String roleName = (request.getRole() != null && !request.getRole().isBlank())
                ? request.getRole().trim().toUpperCase()
                : "BUYER";
        if (roleName.startsWith("ROLE_")) {
            roleName = roleName.substring(5);
        }
        final String searchRoleName = roleName;

        Role role = roleRepository.findByName(searchRoleName)
                .or(() -> roleRepository.findByName("ROLE_" + searchRoleName))
                .orElseGet(() -> {
                    log.info("Khởi tạo vai trò mặc định: {}", searchRoleName);
                    Role newRole = Role.builder()
                            .name(searchRoleName)
                            .description("Vai trò " + searchRoleName)
                            .createdAt(Instant.now())
                            .build();
                    return roleRepository.save(newRole);
                });

        UserRole userRole = UserRole.builder()
                .user(user)
                .role(role)
                .assignedAt(Instant.now())
                .build();
        userRoleRepository.save(userRole);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateAccessToken(userDetails);
        String jti = UUID.randomUUID().toString();
        String refreshToken = jwtService.generateRefreshToken(userDetails, user.getId(), jti);
        saveRefreshTokenInRedis(user.getId(), jti);

        UserResponse userResponse = userMapper.toResponse(user);
        List<String> roles = getUserRoles(user.getId());
        if (userResponse != null) { userResponse.setRoles(roles); }

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getExpirationMs() / 1000)
                .user(userResponse)
                .roles(roles)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String input = request.getUsernameOrEmail() == null ? "" : request.getUsernameOrEmail().trim();
        if (input.isEmpty()) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        User user;
        if (input.contains("@")) {
            String normalizedEmail = input.toLowerCase();
            user = userRepository.findByEmail(normalizedEmail).orElse(null);
        } else {
            user = userRepository.findByPhone(input).orElse(null);
        }

        // Gộp 2 case "user không tồn tại" + "password sai" thành INVALID_CREDENTIALS (401)
        // để tránh user enumeration vulnerability.
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException(AuthErrorCode.USER_DISABLED);
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateAccessToken(userDetails);
        String jti = UUID.randomUUID().toString();
        String refreshToken = jwtService.generateRefreshToken(userDetails, user.getId(), jti);
        saveRefreshTokenInRedis(user.getId(), jti);

        UserResponse userResponse = userMapper.toResponse(user);
        List<String> roles = getUserRoles(user.getId());
        if (userResponse != null) { userResponse.setRoles(roles); }

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getExpirationMs() / 1000)
                .user(userResponse)
                .roles(roles)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        String tokenType;
        String userEmail;
        Long userId;
        String jti;

        try {
            tokenType = jwtService.extractTokenType(refreshToken);
            userEmail = jwtService.extractUsername(refreshToken);
            userId = jwtService.extractUserId(refreshToken);
            jti = jwtService.extractJti(refreshToken);
        } catch (Exception ex) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // Kiểm tra loại token và tính hết hạn
        if (!JwtService.TYPE_REFRESH.equalsIgnoreCase(tokenType) || userEmail == null || jwtService.isTokenExpired(refreshToken)) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // Kiểm tra tồn tại trong Redis (Whitelist refresh token)
        String redisKey = REDIS_REFRESH_KEY_PREFIX + userId + ":" + jti;
        try {
            Boolean exists = stringRedisTemplate.hasKey(redisKey);
            if (Boolean.FALSE.equals(exists)) {
                log.warn("Phát hiện Refresh Token không tồn tại hoặc đã bị thu hồi/xoay vòng trong Redis: userId={}, jti={}", userId, jti);
                throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Không thể kiểm tra Redis Whitelist (Redis có thể đang tắt ở local dev): {}", ex.getMessage());
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
        if (!jwtService.isTokenValid(refreshToken, userDetails)) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException(AuthErrorCode.USER_DISABLED);
        }

        // Rotation: Thu hồi Refresh Token cũ
        revokeRefreshToken(userId, jti);

        // Sinh cặp Access Token + Refresh Token mới
        String newAccessToken = jwtService.generateAccessToken(userDetails);
        String newJti = UUID.randomUUID().toString();
        String newRefreshToken = jwtService.generateRefreshToken(userDetails, userId, newJti);
        saveRefreshTokenInRedis(userId, newJti);

        UserResponse userResponse = userMapper.toResponse(user);
        List<String> roles = getUserRoles(user.getId());
        if (userResponse != null) { userResponse.setRoles(roles); }

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getExpirationMs() / 1000)
                .user(userResponse)
                .roles(roles)
                .build();
    }

    @Override
    public void logout() {
        log.info("Nguoi dung da thuc hien thao tac dang xuat.");
    }

    @Override
    public void logout(RefreshTokenRequest request) {
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            try {
                String token = request.getRefreshToken().trim();
                Long userId = jwtService.extractUserId(token);
                String jti = jwtService.extractJti(token);
                if (userId != null && jti != null) {
                    revokeRefreshToken(userId, jti);
                    log.info("Đã đăng xuất và thu hồi Refresh Token: userId={}, jti={}", userId, jti);
                }
            } catch (Exception ex) {
                log.warn("Lỗi phân tích cú pháp Refresh Token khi logout: {}", ex.getMessage());
            }
        }
    }

    private void saveRefreshTokenInRedis(Long userId, String jti) {
        if (userId == null || jti == null) {
            return;
        }
        try {
            String redisKey = REDIS_REFRESH_KEY_PREFIX + userId + ":" + jti;
            long ttlSeconds = jwtProperties.getRefreshExpirationMs() / 1000;
            stringRedisTemplate.opsForValue().set(redisKey, "VALID", ttlSeconds, TimeUnit.SECONDS);

            String userTokensKey = REDIS_USER_TOKENS_PREFIX + userId;
            stringRedisTemplate.opsForSet().add(userTokensKey, jti);
            stringRedisTemplate.expire(userTokensKey, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.warn("Không thể lưu refresh token vào Redis (Redis có thể đang tắt ở local dev): {}", ex.getMessage());
        }
    }

    private void revokeRefreshToken(Long userId, String jti) {
        try {
            String key = REDIS_REFRESH_KEY_PREFIX + userId + ":" + jti;
            stringRedisTemplate.delete(key);

            String setKey = REDIS_USER_TOKENS_PREFIX + userId;
            stringRedisTemplate.opsForSet().remove(setKey, jti);
        } catch (Exception ex) {
            log.warn("Không thể thu hồi refresh token trong Redis: {}", ex.getMessage());
        }
    }
}
