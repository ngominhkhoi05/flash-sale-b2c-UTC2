package com.b2c.flash_sale_b2c_UTC2.store.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.auth.security.JwtAuthenticationFilter;
import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.config.SecurityConfig;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.store.dto.CreateStoreRequest;
import com.b2c.flash_sale_b2c_UTC2.store.dto.StoreResponse;
import com.b2c.flash_sale_b2c_UTC2.store.dto.UpdateStoreRequest;
import com.b2c.flash_sale_b2c_UTC2.store.service.StoreService;
import com.b2c.flash_sale_b2c_UTC2.user.dto.AddressResponse;
import com.b2c.flash_sale_b2c_UTC2.user.dto.CreateAddressRequest;
import com.b2c.flash_sale_b2c_UTC2.user.dto.UpdateAddressRequest;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StoreController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProperties.class, com.b2c.flash_sale_b2c_UTC2.common.exception.GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=MockSecureSecretForTestingJwtAuthenticationFilter2026"
})
class StoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private StoreService storeService;

    @MockitoBean
    private ImageService imageService;

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
        User seller = User.builder().id(2L).email("seller@example.com").build();
        sellerDetails = new CustomUserDetails(seller, Collections.singletonList(new SimpleGrantedAuthority("ROLE_SELLER")));

        User buyer = User.builder().id(1L).email("buyer@example.com").build();
        buyerDetails = new CustomUserDetails(buyer, Collections.singletonList(new SimpleGrantedAuthority("ROLE_BUYER")));

        when(jwtService.extractTokenType(sellerToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(sellerToken)).thenReturn("seller@example.com");
        when(jwtService.isTokenValid(eq(sellerToken), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("seller@example.com")).thenReturn(sellerDetails);

        when(jwtService.extractTokenType(buyerToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(buyerToken)).thenReturn("buyer@example.com");
        when(jwtService.isTokenValid(eq(buyerToken), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("buyer@example.com")).thenReturn(buyerDetails);
    }

    @Test
    @DisplayName("Chưa đăng nhập gọi API đăng ký gian hàng trả về 401 Unauthorized")
    void registerStore_Unauthenticated_ShouldReturn401() throws Exception {
        CreateStoreRequest request = CreateStoreRequest.builder().storeName("Shop New").build();

        mockMvc.perform(post("/api/v1/stores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("Đăng ký gian hàng thành công trả về 201 Created")
    void registerStore_Authenticated_ShouldReturn201() throws Exception {
        CreateStoreRequest request = CreateStoreRequest.builder().storeName("Shop New").build();
        StoreResponse response = StoreResponse.builder().id(10L).storeName("Shop New").status("PENDING").build();
        when(storeService.registerStore(eq(2L), any(CreateStoreRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.storeName").value("Shop New"));
    }

    @Test
    @DisplayName("Lấy thông tin gian hàng của tôi trả về 200 OK")
    void getMyStore_ShouldReturn200() throws Exception {
        StoreResponse response = StoreResponse.builder().id(10L).storeName("Shop Cong Nghe").build();
        when(storeService.getMyStore(2L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/stores/me")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(10));
    }

    @Test
    @DisplayName("Cập nhật thông tin gian hàng của tôi trả về 200 OK")
    void updateMyStore_ShouldReturn200() throws Exception {
        UpdateStoreRequest request = UpdateStoreRequest.builder().storeName("Shop Updated").build();
        StoreResponse response = StoreResponse.builder().id(10L).storeName("Shop Updated").build();
        when(storeService.updateMyStore(eq(2L), any(UpdateStoreRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/stores/me")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.storeName").value("Shop Updated"));
    }

    @Test
    @DisplayName("Xem thông tin gian hàng công khai không cần token trả về 200 OK")
    void getStoreById_Public_ShouldReturn200() throws Exception {
        StoreResponse response = StoreResponse.builder().id(10L).storeName("Shop Cong Nghe").status("APPROVED").build();
        when(storeService.getStoreById(10L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/stores/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("Lấy danh sách địa chỉ kho của shop trả về 200 OK")
    void getStoreAddresses_ShouldReturn200() throws Exception {
        AddressResponse addressResp = AddressResponse.builder().id(100L).contactName("Kho Tong").build();
        when(storeService.getStoreAddresses(2L)).thenReturn(List.of(addressResp));

        mockMvc.perform(get("/api/v1/stores/me/addresses")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(100));
    }

    @Test
    @DisplayName("Thêm địa chỉ kho cho shop trả về 201 Created")
    void createStoreAddress_ShouldReturn201() throws Exception {
        CreateAddressRequest request = CreateAddressRequest.builder()
                .contactName("Kho Tong")
                .phone("0912345678")
                .province("TP.HCM")
                .district("Thu Duc")
                .ward("Tang Nhon Phu A")
                .detailAddress("450 Le Van Viet")
                .build();

        AddressResponse addressResp = AddressResponse.builder().id(100L).contactName("Kho Tong").build();
        when(storeService.createStoreAddress(eq(2L), any(CreateAddressRequest.class))).thenReturn(addressResp);

        mockMvc.perform(post("/api/v1/stores/me/addresses")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Xóa địa chỉ kho của shop trả về 200 OK")
    void deleteStoreAddress_ShouldReturn200() throws Exception {
        mockMvc.perform(delete("/api/v1/stores/me/addresses/100")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("Buyer gọi API cập nhật gian hàng bị chặn với 403 Forbidden")
    void updateMyStore_BuyerForbidden_ShouldReturn403() throws Exception {
        UpdateStoreRequest request = UpdateStoreRequest.builder().storeName("Shop Hack").build();

        mockMvc.perform(put("/api/v1/stores/me")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("Buyer gọi API lấy địa chỉ kho gian hàng bị chặn với 403 Forbidden")
    void getStoreAddresses_BuyerForbidden_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/stores/me/addresses")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("Buyer gọi API thêm địa chỉ kho gian hàng bị chặn với 403 Forbidden")
    void createStoreAddress_BuyerForbidden_ShouldReturn403() throws Exception {
        CreateAddressRequest request = CreateAddressRequest.builder()
                .contactName("Kho Hack")
                .phone("0912345678")
                .province("TP.HCM")
                .district("Thu Duc")
                .ward("Tang Nhon Phu A")
                .detailAddress("450 Le Van Viet")
                .build();

        mockMvc.perform(post("/api/v1/stores/me/addresses")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }
}
