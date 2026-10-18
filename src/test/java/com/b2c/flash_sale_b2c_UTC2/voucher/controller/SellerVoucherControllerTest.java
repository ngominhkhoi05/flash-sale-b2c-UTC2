package com.b2c.flash_sale_b2c_UTC2.voucher.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.auth.security.JwtAuthenticationFilter;
import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.config.SecurityConfig;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.UpdateVoucherRequest;
import com.b2c.flash_sale_b2c_UTC2.voucher.dto.UpdateVoucherStatusRequest;
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
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SellerVoucherController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "jwt.secret=MockSecureSecretForTestingJwtAuthenticationFilter2026"
})
class SellerVoucherControllerTest {

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

    private CustomUserDetails buyerDetails;
    private CustomUserDetails sellerDetails;

    @BeforeEach
    void setUp() {
        User buyer = User.builder().id(1L).email("buyer@example.com").build();
        buyerDetails = new CustomUserDetails(buyer, Collections.singletonList(new SimpleGrantedAuthority("ROLE_BUYER")));

        User seller = User.builder().id(2L).email("seller@example.com").build();
        sellerDetails = new CustomUserDetails(seller, Collections.singletonList(new SimpleGrantedAuthority("ROLE_SELLER")));

        when(jwtService.extractTokenType(buyerToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(buyerToken)).thenReturn("buyer@example.com");
        when(jwtService.isTokenValid(eq(buyerToken), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("buyer@example.com")).thenReturn(buyerDetails);

        when(jwtService.extractTokenType(sellerToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(sellerToken)).thenReturn("seller@example.com");
        when(jwtService.isTokenValid(eq(sellerToken), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("seller@example.com")).thenReturn(sellerDetails);
    }

    @Test
    @DisplayName("Chưa đăng nhập lấy danh sách voucher seller trả về 401 Unauthorized")
    void getMyStoreVouchers_Unauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/seller/vouchers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("Buyer gọi API lấy danh sách voucher của seller bị 403 Forbidden")
    void getMyStoreVouchers_BuyerForbidden_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/seller/vouchers")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("Seller lấy danh sách voucher của gian hàng thành công trả về 200 OK và PageResponse")
    void getMyStoreVouchers_Seller_ShouldReturn200() throws Exception {
        VoucherResponse v = VoucherResponse.builder()
                .id(1L)
                .code("SHOP20K")
                .storeId(10L)
                .discountType("FIXED_AMOUNT")
                .discountValue(BigDecimal.valueOf(20000))
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

        when(voucherService.getSellerVouchers(eq(2L), eq("ACTIVE"), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/seller/vouchers")
                        .param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.items[0].code").value("SHOP20K"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("Chưa đăng nhập cập nhật trạng thái voucher trả về 401 Unauthorized")
    void updateVoucherStatus_Unauthenticated_ShouldReturn401() throws Exception {
        UpdateVoucherStatusRequest request = new UpdateVoucherStatusRequest("PAUSED");

        mockMvc.perform(patch("/api/v1/seller/vouchers/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("Buyer gọi API cập nhật trạng thái voucher của seller bị 403 Forbidden")
    void updateVoucherStatus_BuyerForbidden_ShouldReturn403() throws Exception {
        UpdateVoucherStatusRequest request = new UpdateVoucherStatusRequest("PAUSED");

        mockMvc.perform(patch("/api/v1/seller/vouchers/1/status")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("Seller cập nhật trạng thái voucher thành công trả về 200 OK")
    void updateVoucherStatus_Seller_ShouldReturn200() throws Exception {
        UpdateVoucherStatusRequest request = new UpdateVoucherStatusRequest("PAUSED");
        VoucherResponse response = VoucherResponse.builder()
                .id(1L)
                .code("SHOP20K")
                .status("PAUSED")
                .build();

        when(voucherService.updateSellerVoucherStatus(eq(2L), eq(1L), any(UpdateVoucherStatusRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/seller/vouchers/1/status")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PAUSED"));
    }

    @Test
    @DisplayName("Seller xem chi tiết voucher thành công trả về 200 OK")
    void getStoreVoucherDetail_Seller_ShouldReturn200() throws Exception {
        VoucherResponse response = VoucherResponse.builder()
                .id(1L)
                .code("SHOP20K")
                .storeId(10L)
                .discountType("FIXED_AMOUNT")
                .discountValue(BigDecimal.valueOf(20000))
                .status("ACTIVE")
                .build();

        when(voucherService.getSellerVoucherDetail(eq(2L), eq(1L))).thenReturn(response);

        mockMvc.perform(get("/api/v1/seller/vouchers/1")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.code").value("SHOP20K"));
    }

    @Test
    @DisplayName("Buyer gọi API xem chi tiết voucher của seller bị 403 Forbidden")
    void getStoreVoucherDetail_Buyer_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/seller/vouchers/1")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("Seller chỉnh sửa voucher thành công trả về 200 OK")
    void updateStoreVoucher_Seller_ShouldReturn200() throws Exception {
        UpdateVoucherRequest request = UpdateVoucherRequest.builder()
                .name("Voucher giảm 25k")
                .discountValue(BigDecimal.valueOf(25000))
                .totalQuantity(150)
                .status("ACTIVE")
                .build();

        VoucherResponse response = VoucherResponse.builder()
                .id(1L)
                .code("SHOP20K")
                .discountValue(BigDecimal.valueOf(25000))
                .totalQuantity(150)
                .status("ACTIVE")
                .build();

        when(voucherService.updateSellerVoucher(eq(2L), eq(1L), any(UpdateVoucherRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/seller/vouchers/1")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalQuantity").value(150));
    }

    @Test
    @DisplayName("Buyer gọi API chỉnh sửa voucher của seller bị 403 Forbidden")
    void updateStoreVoucher_Buyer_ShouldReturn403() throws Exception {
        UpdateVoucherRequest request = UpdateVoucherRequest.builder()
                .name("Hack voucher")
                .build();

        mockMvc.perform(put("/api/v1/seller/vouchers/1")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("Seller xóa voucher thành công trả về 200 OK")
    void deleteStoreVoucher_Seller_ShouldReturn200() throws Exception {
        doNothing().when(voucherService).deleteSellerVoucher(eq(2L), eq(1L));

        mockMvc.perform(delete("/api/v1/seller/vouchers/1")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Xóa mã giảm giá thành công"));
    }

    @Test
    @DisplayName("Buyer gọi API xóa voucher của seller bị 403 Forbidden")
    void deleteStoreVoucher_Buyer_ShouldReturn403() throws Exception {
        mockMvc.perform(delete("/api/v1/seller/vouchers/1")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }
}
