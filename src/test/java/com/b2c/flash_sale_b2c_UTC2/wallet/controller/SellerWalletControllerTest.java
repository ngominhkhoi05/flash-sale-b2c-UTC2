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

@WebMvcTest(controllers = SellerWalletController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "jwt.secret=MockSecureSecretForTestingJwtAuthenticationFilter2026"
})
class SellerWalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private WalletService walletService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private final String sellerToken = "valid_seller_token";
    private final String buyerToken = "valid_buyer_token";

    private CustomUserDetails sellerDetails;
    private CustomUserDetails buyerDetails;

    @BeforeEach
    void setUp() {
        User sellerUser = User.builder().id(20L).email("seller@test.com").build();
        sellerDetails = new CustomUserDetails(
                sellerUser,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_SELLER"))
        );

        User buyerUser = User.builder().id(30L).email("buyer@test.com").build();
        buyerDetails = new CustomUserDetails(
                buyerUser,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_BUYER"))
        );

        when(jwtService.extractTokenType(sellerToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(sellerToken)).thenReturn("seller@test.com");
        when(jwtService.isTokenValid(eq(sellerToken), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("seller@test.com")).thenReturn(sellerDetails);

        when(jwtService.extractTokenType(buyerToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(buyerToken)).thenReturn("buyer@test.com");
        when(jwtService.isTokenValid(eq(buyerToken), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("buyer@test.com")).thenReturn(buyerDetails);
    }

    @Test
    @DisplayName("GET /api/v1/seller/wallet - Thành công cho SELLER")
    void getMyWallet_Success() throws Exception {
        SellerWalletResponse response = SellerWalletResponse.builder()
                .balance(new BigDecimal("10000000.00"))
                .pendingBalance(BigDecimal.ZERO)
                .totalWithdrawn(new BigDecimal("2000000.00"))
                .bankAccount(SellerBankAccount.builder().bankName("MB Bank").isVerified(true).build())
                .build();

        when(walletService.getSellerWallet(20L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/seller/wallet")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.balance").value(10000000.00))
                .andExpect(jsonPath("$.data.bankAccount.bankName").value("MB Bank"));
    }

    @Test
    @DisplayName("GET /api/v1/seller/wallet/transactions - Thành công cho SELLER")
    void getTransactions_Success() throws Exception {
        WalletTransactionResponse tx = WalletTransactionResponse.builder()
                .id(1L)
                .type("WITHDRAWAL")
                .amount(new BigDecimal("-100000.00"))
                .balanceAfter(new BigDecimal("9900000.00"))
                .createdAt(Instant.now())
                .build();

        PageResponse<WalletTransactionResponse> pageResponse = PageResponse.of(
                new PageImpl<>(List.of(tx), PageRequest.of(0, 10), 1)
        );

        when(walletService.getSellerTransactions(eq(20L), any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/seller/wallet/transactions")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].id").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/seller/wallet/withdraw - Thành công cho SELLER")
    void requestWithdrawal_Success() throws Exception {
        SellerWithdrawalRequest request = SellerWithdrawalRequest.builder()
                .amount(new BigDecimal("200000.00"))
                .bankAccountNumber("0123456789")
                .build();

        WalletTransactionResponse response = WalletTransactionResponse.builder()
                .id(2L)
                .type("WITHDRAWAL")
                .amount(new BigDecimal("-200000.00"))
                .balanceAfter(new BigDecimal("9800000.00"))
                .status("SUCCESS")
                .build();

        when(walletService.requestSellerWithdrawal(eq(20L), any(SellerWithdrawalRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/seller/wallet/withdraw")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/seller/wallet - 403 Forbidden cho BUYER")
    void getMyWallet_Forbidden_ForBuyer() throws Exception {
        mockMvc.perform(get("/api/v1/seller/wallet")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden());
    }
}
