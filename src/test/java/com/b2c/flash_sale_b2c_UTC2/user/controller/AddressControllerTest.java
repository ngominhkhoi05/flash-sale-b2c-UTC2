package com.b2c.flash_sale_b2c_UTC2.user.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.auth.security.JwtAuthenticationFilter;
import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.config.SecurityConfig;
import com.b2c.flash_sale_b2c_UTC2.user.dto.AddressResponse;
import com.b2c.flash_sale_b2c_UTC2.user.dto.CreateAddressRequest;
import com.b2c.flash_sale_b2c_UTC2.user.dto.UpdateAddressRequest;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.service.AddressService;
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

@WebMvcTest(controllers = AddressController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "jwt.secret=MockSecureSecretForTestingJwtAuthenticationFilter2026"
})
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AddressService addressService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private final String buyerToken = "valid_buyer_token";
    private CustomUserDetails buyerDetails;

    @BeforeEach
    void setUp() {
        User buyer = User.builder().id(1L).email("buyer@example.com").build();
        buyerDetails = new CustomUserDetails(buyer, Collections.singletonList(new SimpleGrantedAuthority("ROLE_BUYER")));

        when(jwtService.extractTokenType(buyerToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(buyerToken)).thenReturn("buyer@example.com");
        when(jwtService.isTokenValid(eq(buyerToken), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("buyer@example.com")).thenReturn(buyerDetails);
    }

    @Test
    @DisplayName("Chưa đăng nhập truy cập sổ địa chỉ trả về 401 Unauthorized")
    void getUserAddresses_Unauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/users/addresses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("Lấy danh sách sổ địa chỉ với token trả về 200 OK")
    void getUserAddresses_Authenticated_ShouldReturn200() throws Exception {
        AddressResponse addressResp = AddressResponse.builder()
                .id(100L)
                .contactName("User One")
                .phone("0987654321")
                .isDefault(true)
                .build();
        when(addressService.getUserAddresses(1L)).thenReturn(List.of(addressResp));

        mockMvc.perform(get("/api/v1/users/addresses")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(100))
                .andExpect(jsonPath("$.data[0].contactName").value("User One"));
    }

    @Test
    @DisplayName("Thêm địa chỉ mới trả về 201 Created")
    void createAddress_ShouldReturn201() throws Exception {
        CreateAddressRequest request = CreateAddressRequest.builder()
                .contactName("User One")
                .phone("0987654321")
                .province("TP.HCM")
                .district("Thủ Đức")
                .ward("Tăng Nhơn Phú A")
                .detailAddress("450 Lê Văn Việt")
                .isDefault(true)
                .build();

        AddressResponse response = AddressResponse.builder()
                .id(100L)
                .contactName("User One")
                .isDefault(true)
                .build();
        when(addressService.createAddress(eq(1L), any(CreateAddressRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/users/addresses")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100));
    }

    @Test
    @DisplayName("Xem chi tiết một địa chỉ theo ID trả về 200 OK")
    void getAddressById_ShouldReturn200() throws Exception {
        AddressResponse response = AddressResponse.builder().id(100L).contactName("User One").build();
        when(addressService.getAddressById(1L, 100L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/addresses/100")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(100));
    }

    @Test
    @DisplayName("Cập nhật địa chỉ trả về 200 OK")
    void updateAddress_ShouldReturn200() throws Exception {
        UpdateAddressRequest request = UpdateAddressRequest.builder()
                .contactName("User Updated")
                .build();

        AddressResponse response = AddressResponse.builder().id(100L).contactName("User Updated").build();
        when(addressService.updateAddress(eq(1L), eq(100L), any(UpdateAddressRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/users/addresses/100")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.contactName").value("User Updated"));
    }

    @Test
    @DisplayName("Xóa địa chỉ theo ID trả về 200 OK")
    void deleteAddress_ShouldReturn200() throws Exception {
        mockMvc.perform(delete("/api/v1/users/addresses/100")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("Thiết lập địa chỉ mặc định trả về 200 OK")
    void setDefaultAddress_ShouldReturn200() throws Exception {
        AddressResponse response = AddressResponse.builder().id(100L).isDefault(true).build();
        when(addressService.setDefaultAddress(1L, 100L)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/users/addresses/100/default")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isDefault").value(true));
    }
}
