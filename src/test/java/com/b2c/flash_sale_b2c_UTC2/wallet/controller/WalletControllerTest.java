package com.b2c.flash_sale_b2c_UTC2.wallet.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.auth.security.JwtAuthenticationFilter;
import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.config.SecurityConfig;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.SellerBankAccount;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.SellerWalletResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.SellerWithdrawalRequest;
import com.b2c.flash_sale_b2c_UTC2.wallet.dto.WalletTransactionResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WalletController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "jwt.secret=MockSecureSecretForTestingJwtAuthenticationFilter2026"
})
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private WalletService walletService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private final String buyerToken = "valid_buyer_token";
    private CustomUserDetails buyerDetails;

    @BeforeEach
    void setUp() {
        User buyerUser = User.builder().id(50L).email("buyer@test.com").build();
        buyerDetails = new CustomUserDetails(
                buyerUser,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_BUYER"))
        );

        when(jwtService.extractTokenType(buyerToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(buyerToken)).thenReturn("buyer@test.com");
        when(jwtService.isTokenValid(eq(buyerToken), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("buyer@test.com")).thenReturn(buyerDetails);
    }

    @Test
    @DisplayName("GET /api/v1/wallets/me - Thành công cho Buyer")
    void getMyWallet_Success() throws Exception {
        SellerWalletResponse response = SellerWalletResponse.builder()
                .balance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
                .totalWithdrawn(BigDecimal.ZERO)
                .bankAccount(SellerBankAccount.builder().bankName("Chưa liên kết").isVerified(false).build())
                .build();

        when(walletService.getCustomerWallet(50L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/wallets/me")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.balance").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/wallets/me/transactions - Thành công cho Buyer")
    void getTransactions_Success() throws Exception {
        PageResponse<WalletTransactionResponse> pageResponse = PageResponse.of(
                new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0)
        );

        when(walletService.getCustomerTransactions(eq(50L), any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/wallets/me/transactions")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/wallets/me/withdraw - Thành công khi hợp lệ")
    void withdraw_Success() throws Exception {
        SellerWithdrawalRequest request = SellerWithdrawalRequest.builder()
                .amount(new BigDecimal("100000.00"))
                .bankAccountNumber("987654321")
                .build();

        WalletTransactionResponse response = WalletTransactionResponse.builder()
                .id(99L)
                .type("WITHDRAWAL")
                .amount(new BigDecimal("-100000.00"))
                .balanceAfter(BigDecimal.ZERO)
                .status("SUCCESS")
                .createdAt(Instant.now())
                .build();

        when(walletService.requestCustomerWithdrawal(eq(50L), any(SellerWithdrawalRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/wallets/me/withdraw")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(99));
    }
}
