package com.b2c.flash_sale_b2c_UTC2.voucher.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.auth.security.JwtAuthenticationFilter;
import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.config.SecurityConfig;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.ApplyVoucherRequest;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.CreateVoucherRequest;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.VoucherCalculationResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.VoucherResponse;
import com.b2c.flash_sale_b2c_UTC2.voucher.service.VoucherService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {VoucherController.class, SellerVoucherController.class, AdminVoucherController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "jwt.secret=MockSecureSecretForTestingJwtAuthenticationFilter2026"
})
class VoucherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
        objectMapper.findAndRegisterModules();

        User buyer = User.builder().id(1L).email("buyer@example.com").build();
        buyerDetails = new CustomUserDetails(buyer, Collections.singletonList(new SimpleGrantedAuthority("ROLE_BUYER")));

        User seller = User.builder().id(2L).email("seller@example.com").build();
        sellerDetails = new CustomUserDetails(seller, Collections.singletonList(new SimpleGrantedAuthority("ROLE_SELLER")));

        User admin = User.builder().id(3L).email("admin@example.com").build();
        adminDetails = new CustomUserDetails(admin, Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

        // Mock Buyer
        when(jwtService.extractTokenType(buyerToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(buyerToken)).thenReturn("buyer@example.com");
        when(userDetailsService.loadUserByUsername("buyer@example.com")).thenReturn(buyerDetails);
        when(jwtService.isTokenValid(buyerToken, buyerDetails)).thenReturn(true);

        // Mock Seller
        when(jwtService.extractTokenType(sellerToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(sellerToken)).thenReturn("seller@example.com");
        when(userDetailsService.loadUserByUsername("seller@example.com")).thenReturn(sellerDetails);
        when(jwtService.isTokenValid(sellerToken, sellerDetails)).thenReturn(true);

        // Mock Admin
        when(jwtService.extractTokenType(adminToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(adminToken)).thenReturn("admin@example.com");
        when(userDetailsService.loadUserByUsername("admin@example.com")).thenReturn(adminDetails);
        when(jwtService.isTokenValid(adminToken, adminDetails)).thenReturn(true);
    }

    @Test
    @DisplayName("Xem danh sách voucher toàn sàn có token Buyer -> 200 OK")
    void getPlatformVouchers_WithToken_ShouldReturn200() throws Exception {
        VoucherResponse voucher = VoucherResponse.builder().id(100L).code("PLATFORM50").build();
        when(voucherService.getPlatformVouchers()).thenReturn(List.of(voucher));

        mockMvc.perform(get("/api/v1/vouchers/platform")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("PLATFORM50"));
    }

    @Test
    @DisplayName("Xem danh sách voucher của cửa hàng có token Buyer -> 200 OK")
    void getStoreVouchers_WithToken_ShouldReturn200() throws Exception {
        VoucherResponse voucher = VoucherResponse.builder().id(101L).code("STORE10").build();
        when(voucherService.getStoreVouchers(10L)).thenReturn(List.of(voucher));

        mockMvc.perform(get("/api/v1/vouchers/store/10")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("STORE10"));
    }

    @Test
    @DisplayName("Kiểm tra áp dụng voucher (apply) -> 200 OK")
    void validateAndCalculate_WithToken_ShouldReturn200() throws Exception {
        ApplyVoucherRequest request = ApplyVoucherRequest.builder()
                .code("UTC2DISCOUNT")
                .storeId(10L)
                .subtotalAmount(new BigDecimal("300000.00"))
                .build();

        VoucherCalculationResponse response = VoucherCalculationResponse.builder()
                .voucherId(100L)
                .code("UTC2DISCOUNT")
                .discountAmount(new BigDecimal("50000.00"))
                .finalAmount(new BigDecimal("250000.00"))
                .build();

        when(voucherService.validateAndCalculate(eq(1L), any(ApplyVoucherRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/vouchers/apply")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.discountAmount").value(50000.00));
    }

    @Test
    @DisplayName("Buyer cố tạo voucher seller (/api/v1/seller/vouchers) -> 403 Forbidden")
    void createStoreVoucher_WithBuyerRole_ShouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/seller/vouchers")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Seller tạo voucher gian hàng thành công -> 201 Created")
    void createStoreVoucher_WithSellerRole_ShouldReturn201() throws Exception {
        Instant now = Instant.now();
        CreateVoucherRequest request = CreateVoucherRequest.builder()
                .code("SHOP20")
                .storeId(10L)
                .discountType("FIXED_AMOUNT")
                .discountValue(new BigDecimal("20000.00"))
                .minOrderAmount(new BigDecimal("100000.00"))
                .totalQuantity(100)
                .userUsageLimit(1)
                .startTime(now)
                .endTime(now.plus(7, ChronoUnit.DAYS))
                .build();

        VoucherResponse response = VoucherResponse.builder().id(200L).code("SHOP20").build();
        when(voucherService.createVoucher(eq(2L), any(CreateVoucherRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/seller/vouchers")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("SHOP20"));
    }

    @Test
    @DisplayName("Seller xem danh sách voucher của gian hàng mình -> 200 OK")
    void getMyStoreVouchers_WithSellerRole_ShouldReturn200() throws Exception {
        VoucherResponse response = VoucherResponse.builder().id(200L).code("SHOP20").build();
        when(voucherService.getMyStoreVouchers(2L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/seller/vouchers")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("SHOP20"));
    }

    @Test
    @DisplayName("Seller cố tạo voucher sàn (/api/v1/admin/vouchers) -> 403 Forbidden")
    void createAdminVoucher_WithSellerRole_ShouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/vouchers")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Admin tạo voucher sàn thành công -> 201 Created")
    void createAdminVoucher_WithAdminRole_ShouldReturn201() throws Exception {
        Instant now = Instant.now();
        CreateVoucherRequest request = CreateVoucherRequest.builder()
                .code("PLATFORM100")
                .discountType("FIXED_AMOUNT")
                .discountValue(new BigDecimal("100000.00"))
                .minOrderAmount(new BigDecimal("500000.00"))
                .totalQuantity(50)
                .userUsageLimit(1)
                .startTime(now)
                .endTime(now.plus(30, ChronoUnit.DAYS))
                .build();

        VoucherResponse response = VoucherResponse.builder().id(300L).code("PLATFORM100").build();
        when(voucherService.createVoucher(eq(3L), any(CreateVoucherRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/vouchers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("PLATFORM100"));
    }
}
