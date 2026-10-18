package com.b2c.flash_sale_b2c_UTC2.wallet.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.auth.security.JwtAuthenticationFilter;
import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.config.SecurityConfig;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.AdminWithdrawalResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.ApproveWithdrawalRequest;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.RejectWithdrawalRequest;
import com.b2c.flash_sale_b2c_UTC2.wallet.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminWalletController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "jwt.secret=MockSecureSecretForTestingJwtAuthenticationFilter2026"
})
class AdminWalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private WalletService walletService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private final String adminToken = "valid_admin_token";
    private final String sellerToken = "valid_seller_token";

    private CustomUserDetails adminDetails;
    private CustomUserDetails sellerDetails;

    @BeforeEach
    void setUp() {
        User adminUser = User.builder().id(1L).email("admin@test.com").build();
        adminDetails = new CustomUserDetails(
                adminUser,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        User sellerUser = User.builder().id(20L).email("seller@test.com").build();
        sellerDetails = new CustomUserDetails(
                sellerUser,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_SELLER"))
        );

        when(jwtService.extractTokenType(adminToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(adminToken)).thenReturn("admin@test.com");
        when(jwtService.isTokenValid(eq(adminToken), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("admin@test.com")).thenReturn(adminDetails);

        when(jwtService.extractTokenType(sellerToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(sellerToken)).thenReturn("seller@test.com");
        when(jwtService.isTokenValid(eq(sellerToken), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("seller@test.com")).thenReturn(sellerDetails);
    }

    @Test
    @DisplayName("GET /api/v1/admin/wallets/withdrawals - Thành công cho ADMIN")
    void getWithdrawals_Success() throws Exception {
        AdminWithdrawalResponse item = AdminWithdrawalResponse.builder()
                .id(10L)
                .requestCode("WDR-261018-ABCD")
                .storeId(2L)
                .storeName("UTC Store")
                .amount(new BigDecimal("500000.00"))
                .status("PENDING")
                .bankName("Vietcombank")
                .accountNumber("102988391204")
                .accountHolder("NGUYEN VAN BAN")
                .createdAt(Instant.now())
                .build();

        PageResponse<AdminWithdrawalResponse> pageResponse = PageResponse.of(
                new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1)
        );

        when(walletService.getAdminWithdrawals(any(), any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/wallets/withdrawals")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].requestCode").value("WDR-261018-ABCD"))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/wallets/withdrawals/{id}/approve - Thành công cho ADMIN")
    void approveWithdrawal_Success() throws Exception {
        AdminWithdrawalResponse item = AdminWithdrawalResponse.builder()
                .id(10L)
                .requestCode("WDR-261018-ABCD")
                .status("APPROVED")
                .adminNote("Đã chuyển khoản UNC-9921")
                .approvedAt(Instant.now())
                .build();

        when(walletService.approveWithdrawal(eq(10L), eq(1L), any(ApproveWithdrawalRequest.class))).thenReturn(item);

        ApproveWithdrawalRequest request = ApproveWithdrawalRequest.builder()
                .adminNote("Đã chuyển khoản UNC-9921")
                .build();

        mockMvc.perform(patch("/api/v1/admin/wallets/withdrawals/10/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/wallets/withdrawals/{id}/reject - Thành công cho ADMIN")
    void rejectWithdrawal_Success() throws Exception {
        AdminWithdrawalResponse item = AdminWithdrawalResponse.builder()
                .id(10L)
                .requestCode("WDR-261018-ABCD")
                .status("REJECTED")
                .adminNote("Số tài khoản thụ hưởng không đúng")
                .rejectedAt(Instant.now())
                .build();

        when(walletService.rejectWithdrawal(eq(10L), eq(1L), any(RejectWithdrawalRequest.class))).thenReturn(item);

        RejectWithdrawalRequest request = RejectWithdrawalRequest.builder()
                .reason("Số tài khoản thụ hưởng không đúng")
                .build();

        mockMvc.perform(patch("/api/v1/admin/wallets/withdrawals/10/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/wallets/withdrawals - 403 Forbidden cho SELLER")
    void getWithdrawals_Forbidden_ForSeller() throws Exception {
        mockMvc.perform(get("/api/v1/admin/wallets/withdrawals")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isForbidden());
    }
}
