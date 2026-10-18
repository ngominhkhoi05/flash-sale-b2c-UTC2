package com.b2c.flash_sale_b2c_UTC2.voucher.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.auth.security.JwtAuthenticationFilter;
import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.config.SecurityConfig;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.AdminVoucherKpisResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.VoucherResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.service.VoucherService;
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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminVoucherController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "jwt.secret=MockSecureSecretForTestingJwtAuthenticationFilter2026"
})
class AdminVoucherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VoucherService voucherService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private final String buyerToken = "valid_buyer_token";
    private final String sellerToken = "valid_seller_token";
    private final String adminToken = "valid_admin_token";

    private CustomUserDetails buyerDetails;
    private CustomUserDetails sellerDetails;
    private CustomUserDetails adminDetails;

    @BeforeEach
    void setUp() {
        User buyer = User.builder().id(1L).email("buyer@example.com").build();
        buyerDetails = new CustomUserDetails(buyer, Collections.singletonList(new SimpleGrantedAuthority("ROLE_BUYER")));

        User seller = User.builder().id(2L).email("seller@example.com").build();
        sellerDetails = new CustomUserDetails(seller, Collections.singletonList(new SimpleGrantedAuthority("ROLE_SELLER")));

        User admin = User.builder().id(3L).email("admin@example.com").build();
        adminDetails = new CustomUserDetails(admin, Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

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
    @DisplayName("Chưa đăng nhập lấy danh sách voucher admin trả về 401 Unauthorized")
    void getAdminVouchers_Unauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/vouchers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("Seller gọi API lấy danh sách voucher admin bị 403 Forbidden")
    void getAdminVouchers_SellerForbidden_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/vouchers")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("Admin lấy danh sách voucher thành công trả về 200 OK và PageResponse")
    void getAdminVouchers_Admin_ShouldReturn200() throws Exception {
        VoucherResponse v = VoucherResponse.builder()
                .id(10L)
                .code("PLATFORM50K")
                .discountType("FIXED_AMOUNT")
                .discountValue(BigDecimal.valueOf(50000))
                .status("ACTIVE")
                .build();

        PageResponse<VoucherResponse> pageResponse = PageResponse.<VoucherResponse>builder()
                .items(List.of(v))
                .pageNumber(0)
                .pageSize(10)
                .totalElements(1)
                .totalPages(1)
                .isFirst(true)
                .isLast(true)
                .build();

        when(voucherService.getAdminVouchers(eq("PLATFORM"), eq("ACTIVE"), any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/vouchers")
                        .param("scope", "PLATFORM")
                        .param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].id").value(10))
                .andExpect(jsonPath("$.data.items[0].code").value("PLATFORM50K"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("Chưa đăng nhập lấy KPIs voucher admin trả về 401 Unauthorized")
    void getAdminVoucherKpis_Unauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/vouchers/kpis"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("Buyer gọi API lấy KPIs voucher admin bị 403 Forbidden")
    void getAdminVoucherKpis_BuyerForbidden_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/vouchers/kpis")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("Admin lấy KPIs voucher thành công trả về 200 OK và các chỉ số")
    void getAdminVoucherKpis_Admin_ShouldReturn200() throws Exception {
        AdminVoucherKpisResponse kpis = AdminVoucherKpisResponse.builder()
                .totalVouchers(45)
                .activeVouchers(28)
                .totalDistributedBudget(BigDecimal.valueOf(500000000))
                .usedBudget(BigDecimal.valueOf(182400000))
                .totalUsages(18420)
                .build();

        when(voucherService.getAdminVoucherKpis()).thenReturn(kpis);

        mockMvc.perform(get("/api/v1/admin/vouchers/kpis")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalVouchers").value(45))
                .andExpect(jsonPath("$.data.activeVouchers").value(28))
                .andExpect(jsonPath("$.data.totalUsages").value(18420));
    }
}
