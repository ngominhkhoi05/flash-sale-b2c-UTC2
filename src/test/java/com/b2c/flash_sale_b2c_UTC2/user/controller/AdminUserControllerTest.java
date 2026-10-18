package com.b2c.flash_sale_b2c_UTC2.user.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.auth.security.JwtAuthenticationFilter;
import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.config.SecurityConfig;
import com.b2c.flash_sale_b2c_UTC2.user.dto.AdminUserResponse;
import com.b2c.flash_sale_b2c_UTC2.user.dto.UpdateUserStatusRequest;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminUserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "jwt.secret=MockSecureSecretForTestingJwtAuthenticationFilter2026"
})
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private final String buyerToken = "valid_buyer_token";
    private final String sellerToken = "valid_seller_token";
    private final String adminToken = "valid_admin_token";

    @BeforeEach
    void setUp() {
        User buyer = User.builder().id(1L).email("buyer@example.com").build();
        CustomUserDetails buyerDetails = new CustomUserDetails(buyer, Collections.singletonList(new SimpleGrantedAuthority("ROLE_BUYER")));

        User seller = User.builder().id(2L).email("seller@example.com").build();
        CustomUserDetails sellerDetails = new CustomUserDetails(seller, Collections.singletonList(new SimpleGrantedAuthority("ROLE_SELLER")));

        User admin = User.builder().id(3L).email("admin@example.com").build();
        CustomUserDetails adminDetails = new CustomUserDetails(admin, Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

        when(jwtService.extractTokenType(buyerToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(buyerToken)).thenReturn("buyer@example.com");
        when(jwtService.isTokenValid(eq(buyerToken), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("buyer@example.com")).thenReturn(buyerDetails);

        when(jwtService.extractTokenType(sellerToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(sellerToken)).thenReturn("seller@example.com");
        when(jwtService.isTokenValid(eq(sellerToken), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("seller@example.com")).thenReturn(sellerDetails);

        when(jwtService.extractTokenType(adminToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(adminToken)).thenReturn("admin@example.com");
        when(jwtService.isTokenValid(eq(adminToken), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("admin@example.com")).thenReturn(adminDetails);
    }

    @Test
    @DisplayName("Chưa đăng nhập gọi GET /api/v1/admin/users trả về 401 Unauthorized")
    void getAdminUsers_Unauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("Buyer gọi GET /api/v1/admin/users bị 403 Forbidden")
    void getAdminUsers_Buyer_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("Admin lấy danh sách người dùng thành công trả về 200 OK và PageResponse")
    void getAdminUsers_Admin_ShouldReturn200() throws Exception {
        AdminUserResponse userResponse = AdminUserResponse.builder()
                .id(1L)
                .email("buyer@example.com")
                .fullName("Nguyễn Khách Hàng")
                .role("BUYER")
                .roles(List.of("BUYER"))
                .status("ACTIVE")
                .ordersCount(5L)
                .build();
        PageResponse<AdminUserResponse> pageResponse = PageResponse.<AdminUserResponse>builder()
                .items(List.of(userResponse))
                .pageNumber(0)
                .pageSize(10)
                .totalElements(1)
                .totalPages(1)
                .isFirst(true)
                .isLast(true)
                .build();

        when(userService.getAdminUsers(eq("BUYER"), eq("ACTIVE"), eq("Nguyễn"), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("role", "BUYER")
                        .param("status", "ACTIVE")
                        .param("search", "Nguyễn")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].email").value("buyer@example.com"))
                .andExpect(jsonPath("$.data.items[0].role").value("BUYER"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("Admin cập nhật trạng thái người dùng thành công trả về 200 OK")
    void updateUserStatus_Admin_ShouldReturn200() throws Exception {
        UpdateUserStatusRequest request = new UpdateUserStatusRequest("LOCKED", "Vi phạm điều khoản sàn");
        AdminUserResponse response = AdminUserResponse.builder()
                .id(10L)
                .email("violator@example.com")
                .status("LOCKED")
                .role("BUYER")
                .build();

        when(userService.updateUserStatus(eq(10L), any(UpdateUserStatusRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/v1/admin/users/10/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("LOCKED"));
    }
}
