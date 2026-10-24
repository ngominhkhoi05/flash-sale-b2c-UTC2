package com.b2c.flash_sale_b2c_UTC2.search.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.auth.security.JwtAuthenticationFilter;
import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.config.SecurityConfig;
import com.b2c.flash_sale_b2c_UTC2.search.service.SearchIndexScheduler;
import com.b2c.flash_sale_b2c_UTC2.search.service.SearchIndexService;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test cho {@link AdminSearchController} — endpoint quản trị chỉ mục OpenSearch.
 *
 * <p>Verify 3 tầng bảo vệ: 401 (chưa đăng nhập), 403 (không phải admin),
 * 200 (admin) + payload đúng.
 */
@WebMvcTest(controllers = AdminSearchController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "jwt.secret=MockSecureSecretForTestingJwtAuthenticationFilter2026"
})
class AdminSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchIndexScheduler searchIndexScheduler;

    @MockitoBean
    private SearchIndexService searchIndexService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private final String buyerToken = "valid_buyer_token";
    private final String adminToken = "valid_admin_token";

    @BeforeEach
    void setUp() {
        User buyer = User.builder().id(1L).email("buyer@example.com").build();
        CustomUserDetails buyerDetails = new CustomUserDetails(buyer,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_BUYER")));
        when(jwtService.extractTokenType(buyerToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(buyerToken)).thenReturn("buyer@example.com");
        when(jwtService.isTokenValid(eq(buyerToken), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("buyer@example.com")).thenReturn(buyerDetails);

        User admin = User.builder().id(2L).email("admin@example.com").build();
        CustomUserDetails adminDetails = new CustomUserDetails(admin,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(jwtService.extractTokenType(adminToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(adminToken)).thenReturn("admin@example.com");
        when(jwtService.isTokenValid(eq(adminToken), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("admin@example.com")).thenReturn(adminDetails);
    }

    @Test
    @DisplayName("Chua dang nhap goi POST /api/v1/admin/search/reindex tra ve 401 Unauthorized")
    void reindex_Unauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/search/reindex"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("Buyer goi POST /api/v1/admin/search/reindex bi 403 Forbidden")
    void reindex_BuyerForbidden_ShouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/search/reindex")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("Admin goi POST /api/v1/admin/search/reindex tra ve 200 kem so document da index")
    void reindex_Admin_ShouldReturn200WithCounts() throws Exception {
        when(searchIndexScheduler.fullReindex()).thenReturn(120);
        when(searchIndexService.countDocuments()).thenReturn(120L);

        mockMvc.perform(post("/api/v1/admin/search/reindex")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.indexedCount").value(120))
                .andExpect(jsonPath("$.data.indexCount").value(120));
    }

    @Test
    @DisplayName("Chua dang nhap goi GET /api/v1/admin/search/status tra ve 401 Unauthorized")
    void status_Unauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/search/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("Buyer goi GET /api/v1/admin/search/status bi 403 Forbidden")
    void status_BuyerForbidden_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/search/status")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("Admin goi GET /api/v1/admin/search/status tra ve 200 kem so document trong index")
    void status_Admin_ShouldReturn200WithIndexCount() throws Exception {
        when(searchIndexService.countDocuments()).thenReturn(42L);

        mockMvc.perform(get("/api/v1/admin/search/status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.indexedCount").value(0))
                .andExpect(jsonPath("$.data.indexCount").value(42));
    }

    @Test
    @DisplayName("OpenSearch khong phan hoi -> tra 503 SERVICE_UNAVAILABLE (khong gia ve 200)")
    void status_Admin_WhenOpenSearchDown_Returns503() throws Exception {
        when(searchIndexService.countDocuments()).thenReturn(-1L);

        mockMvc.perform(get("/api/v1/admin/search/status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(503));
    }

    @Test
    @DisplayName("Reindex khong ghi duoc document nao va OpenSearch down -> tra 503")
    void reindex_Admin_WhenOpenSearchDown_Returns503() throws Exception {
        when(searchIndexScheduler.fullReindex()).thenReturn(0);
        when(searchIndexService.countDocuments()).thenReturn(-1L);

        mockMvc.perform(post("/api/v1/admin/search/reindex")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(503));
    }
}
