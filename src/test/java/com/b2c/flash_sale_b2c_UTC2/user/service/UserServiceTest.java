package com.b2c.flash_sale_b2c_UTC2.user.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.user.dto.ChangePasswordRequest;
import com.b2c.flash_sale_b2c_UTC2.user.dto.UpdateProfileRequest;
import com.b2c.flash_sale_b2c_UTC2.user.dto.UserResponse;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.exception.UserErrorCode;
import com.b2c.flash_sale_b2c_UTC2.user.mapper.UserMapper;
import com.b2c.flash_sale_b2c_UTC2.user.repository.UserRepository;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.b2c.flash_sale_b2c_UTC2.user.repository.UserRoleRepository userRoleRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ImageService imageService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private UserServiceImpl userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hashed_old_pwd")
                .fullName("User Test")
                .phone("0912345678")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Lấy thông tin profile người dùng thành công")
    void getProfile_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userMapper.toResponse(sampleUser)).thenReturn(UserResponse.builder().id(1L).email("user@example.com").build());

        UserResponse response = userService.getProfile(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
    }

    @Test
    @DisplayName("Cập nhật thông tin profile thành công")
    void updateProfile_Success() {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("User Updated")
                .phone("0988888888")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.findByPhone("0988888888")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(userMapper.toResponse(sampleUser)).thenReturn(UserResponse.builder().id(1L).fullName("User Updated").build());

        UserResponse response = userService.updateProfile(1L, request);

        assertNotNull(response);
        assertEquals("User Updated", response.getFullName());
    }

    @Test
    @DisplayName("Đổi mật khẩu thành công và thu hồi toàn bộ refresh token trong Redis")
    void changePassword_Success_ShouldRevokeAllUserRefreshTokens() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .oldPassword("oldPassword123")
                .newPassword("newPassword123")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("oldPassword123", "hashed_old_pwd")).thenReturn(true);
        when(passwordEncoder.encode("newPassword123")).thenReturn("hashed_new_pwd");
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("auth:user_tokens:1")).thenReturn(Set.of("jti-1", "jti-2"));

        userService.changePassword(1L, request);

        verify(userRepository).save(sampleUser);
        verify(stringRedisTemplate).delete(argThat((java.util.Collection<String> keys) ->
                keys.size() == 2 && keys.contains("auth:refresh:1:jti-1") && keys.contains("auth:refresh:1:jti-2")));
        verify(stringRedisTemplate).delete("auth:user_tokens:1");
    }

    @Test
    @DisplayName("Đổi mật khẩu thất bại khi mật khẩu cũ không đúng")
    void changePassword_ShouldThrowException_WhenOldPasswordIncorrect() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .oldPassword("wrongPassword")
                .newPassword("newPassword123")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrongPassword", "hashed_old_pwd")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> userService.changePassword(1L, request));
        assertEquals(UserErrorCode.OLD_PASSWORD_INCORRECT.getCode(), exception.getErrorCode().getCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Lấy hồ sơ người dùng thất bại khi không tìm thấy USER_NOT_FOUND (404)")
    void getProfile_ShouldThrowException_WhenUserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> userService.getProfile(999L));
        assertEquals(UserErrorCode.USER_NOT_FOUND.getCode(), exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Cập nhật hồ sơ thất bại khi số điện thoại bị trùng với người dùng khác")
    void updateProfile_ShouldThrowException_WhenPhoneAlreadyExists() {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("User Test")
                .phone("0988888888")
                .build();

        User anotherUser = User.builder().id(2L).phone("0988888888").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.findByPhone("0988888888")).thenReturn(Optional.of(anotherUser));

        assertThrows(BusinessException.class, () -> userService.updateProfile(1L, request));
        verify(userRepository, never()).save(any(User.class));
    }
}
