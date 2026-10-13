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
import com.b2c.flash_sale_b2c_UTC2.user.mapper.UserMapper;
import com.b2c.flash_sale_b2c_UTC2.user.repository.RoleRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Mock
    private org.springframework.data.redis.core.ValueOperations<String, String> valueOperations;

    @Mock
    private org.springframework.data.redis.core.SetOperations<String, String> setOperations;

    @InjectMocks
    private AuthServiceImpl authService;

    private User sampleUser;
    private Role sampleRole;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .passwordHash("encoded_pwd")
                .fullName("Nguyễn Văn A")
                .phone("0987654321")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        sampleRole = Role.builder()
                .id((short) 1)
                .name("BUYER")
                .description("Vai trò Người mua")
                .build();

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    @DisplayName("Đăng ký thành công trả về AuthResponse chứa token")
    void register_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .email("new@example.com")
                .password("password123")
                .fullName("Nguyễn Văn A")
                .phone("0987654321")
                .role("BUYER")
                .build();

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("0987654321")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded_pwd");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(roleRepository.findByName("BUYER")).thenReturn(Optional.of(sampleRole));
        when(jwtProperties.getExpirationMs()).thenReturn(3600000L);
        when(jwtProperties.getRefreshExpirationMs()).thenReturn(604800000L);

        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                "test@example.com", "encoded_pwd", Collections.emptyList());
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(userDetails);
        when(jwtService.generateAccessToken(userDetails)).thenReturn("mock_access_token");
        when(jwtService.generateRefreshToken(eq(userDetails), eq(1L), anyString())).thenReturn("mock_refresh_token");
        when(userMapper.toResponse(sampleUser)).thenReturn(UserResponse.builder().id(1L).email("test@example.com").build());

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mock_access_token", response.getAccessToken());
        assertEquals("mock_refresh_token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        verify(userRepository).save(any(User.class));
        verify(userRoleRepository).save(any());
        verify(valueOperations).set(startsWith("auth:refresh:1:"), eq("VALID"), anyLong(), any());
    }

    @Test
    @DisplayName("Đăng ký thất bại khi email đã tồn tại")
    void register_ShouldThrowException_WhenEmailExists() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("password123")
                .fullName("Nguyễn Văn A")
                .build();

        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.register(request));
        assertEquals(AuthErrorCode.EMAIL_ALREADY_EXISTS.getCode(), exception.getErrorCode().getCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Đăng nhập thành công với thông tin chính xác")
    void login_Success() {
        LoginRequest request = LoginRequest.builder()
                .usernameOrEmail("test@example.com")
                .password("password123")
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("password123", "encoded_pwd")).thenReturn(true);
        when(jwtProperties.getExpirationMs()).thenReturn(3600000L);
        when(jwtProperties.getRefreshExpirationMs()).thenReturn(604800000L);

        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                "test@example.com", "encoded_pwd", Collections.emptyList());
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);
        when(jwtService.generateAccessToken(userDetails)).thenReturn("mock_access_token");
        when(jwtService.generateRefreshToken(eq(userDetails), eq(1L), anyString())).thenReturn("mock_refresh_token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock_access_token", response.getAccessToken());
        assertEquals("mock_refresh_token", response.getRefreshToken());
        verify(valueOperations).set(startsWith("auth:refresh:1:"), eq("VALID"), anyLong(), any());
    }

    @Test
    @DisplayName("Đăng nhập thất bại khi email không tồn tại trên hệ thống (404)")
    void login_ShouldThrowException_WhenEmailNotFound() {
        LoginRequest request = LoginRequest.builder()
                .usernameOrEmail("notfound@example.com")
                .password("password123")
                .build();

        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(AuthErrorCode.USER_NOT_FOUND.getCode(), exception.getErrorCode().getCode());
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getErrorCode().getHttpStatus());
    }

    @Test
    @DisplayName("Đăng nhập thất bại khi số điện thoại không tồn tại trên hệ thống (404)")
    void login_ShouldThrowException_WhenPhoneNotFound() {
        LoginRequest request = LoginRequest.builder()
                .usernameOrEmail("0988776655")
                .password("password123")
                .build();

        when(userRepository.findByPhone("0988776655")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(AuthErrorCode.USER_NOT_FOUND.getCode(), exception.getErrorCode().getCode());
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getErrorCode().getHttpStatus());
    }

    @Test
    @DisplayName("Đăng nhập thất bại khi sai mật khẩu")
    void login_ShouldThrowException_WhenPasswordIsIncorrect() {
        LoginRequest request = LoginRequest.builder()
                .usernameOrEmail("test@example.com")
                .password("wrong_password")
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrong_password", "encoded_pwd")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(AuthErrorCode.INVALID_CREDENTIALS.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Đăng nhập thất bại khi tài khoản bị khóa")
    void login_ShouldThrowException_WhenAccountIsDisabled() {
        sampleUser.setStatus("LOCKED");
        LoginRequest request = LoginRequest.builder()
                .usernameOrEmail("test@example.com")
                .password("password123")
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("password123", "encoded_pwd")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(AuthErrorCode.USER_DISABLED.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Làm mới token thất bại khi refresh token không hợp lệ")
    void refreshToken_ShouldThrowException_WhenTokenIsInvalid() {
        RefreshTokenRequest request = new RefreshTokenRequest("invalid_token");
        when(jwtService.extractTokenType("invalid_token")).thenThrow(new RuntimeException("Malformed token"));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.refreshToken(request));
        assertEquals(AuthErrorCode.INVALID_REFRESH_TOKEN.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Làm mới token thành công với cơ chế rotation và xác thực Redis")
    void refreshToken_Success() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid_refresh_token");

        when(jwtService.extractTokenType("valid_refresh_token")).thenReturn("REFRESH");
        when(jwtService.isTokenExpired("valid_refresh_token")).thenReturn(false);
        when(jwtService.extractUsername("valid_refresh_token")).thenReturn("test@example.com");
        when(jwtService.extractUserId("valid_refresh_token")).thenReturn(1L);
        when(jwtService.extractJti("valid_refresh_token")).thenReturn("old-jti-uuid");
        when(stringRedisTemplate.hasKey("auth:refresh:1:old-jti-uuid")).thenReturn(true);

        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                "test@example.com", "encoded_pwd", Collections.emptyList());
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid("valid_refresh_token", userDetails)).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));

        when(jwtService.generateAccessToken(userDetails)).thenReturn("new_access_token");
        when(jwtService.generateRefreshToken(eq(userDetails), eq(1L), anyString())).thenReturn("new_refresh_token");
        when(jwtProperties.getExpirationMs()).thenReturn(3600000L);
        when(jwtProperties.getRefreshExpirationMs()).thenReturn(604800000L);
        when(userMapper.toResponse(sampleUser)).thenReturn(UserResponse.builder().id(1L).email("test@example.com").build());

        AuthResponse response = authService.refreshToken(request);

        assertNotNull(response);
        assertEquals("new_access_token", response.getAccessToken());
        assertEquals("new_refresh_token", response.getRefreshToken());
        // Kiểm tra xóa token cũ trong Redis
        verify(stringRedisTemplate).delete("auth:refresh:1:old-jti-uuid");
        // Kiểm tra lưu token mới trong Redis
        verify(valueOperations).set(startsWith("auth:refresh:1:"), eq("VALID"), anyLong(), any());
    }

    @Test
    @DisplayName("Làm mới token thất bại khi token không tồn tại trong Redis (đã logout hoặc rotation trước đó)")
    void refreshToken_ShouldThrowException_WhenNotInRedis() {
        RefreshTokenRequest request = new RefreshTokenRequest("revoked_refresh_token");

        when(jwtService.extractTokenType("revoked_refresh_token")).thenReturn("REFRESH");
        when(jwtService.isTokenExpired("revoked_refresh_token")).thenReturn(false);
        when(jwtService.extractUsername("revoked_refresh_token")).thenReturn("test@example.com");
        when(jwtService.extractUserId("revoked_refresh_token")).thenReturn(1L);
        when(jwtService.extractJti("revoked_refresh_token")).thenReturn("stolen-jti-uuid");
        when(stringRedisTemplate.hasKey("auth:refresh:1:stolen-jti-uuid")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.refreshToken(request));
        assertEquals(AuthErrorCode.INVALID_REFRESH_TOKEN.getCode(), exception.getErrorCode().getCode());
        verify(stringRedisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("Đăng xuất thành công thu hồi token trong Redis")
    void logout_Success() {
        RefreshTokenRequest request = new RefreshTokenRequest("logout_refresh_token");
        when(jwtService.extractUserId("logout_refresh_token")).thenReturn(1L);
        when(jwtService.extractJti("logout_refresh_token")).thenReturn("jti-to-revoke");

        authService.logout(request);

        verify(stringRedisTemplate).delete("auth:refresh:1:jti-to-revoke");
        verify(setOperations).remove("auth:user_tokens:1", "jti-to-revoke");
    }
}
