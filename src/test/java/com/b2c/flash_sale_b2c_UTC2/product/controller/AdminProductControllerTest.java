package com.b2c.flash_sale_b2c_UTC2.product.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.auth.security.JwtAuthenticationFilter;
import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.config.SecurityConfig;
import com.b2c.flash_sale_b2c_UTC2.product.dto.AdminProductResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.UpdateProductStatusRequest;
import com.b2c.flash_sale_b2c_UTC2.product.service.ProductService;
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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminProductController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "jwt.secret=MockSecureSecretForTestingJwtAuthenticationFilter2026"
})
class AdminProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ProductService productService;

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
    @DisplayName("Chưa đăng nhập lấy danh sách sản phẩm admin trả về 401 Unauthorized")
    void getAdminProducts_Unauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("Buyer gọi API lấy danh sách sản phẩm admin bị 403 Forbidden")
    void getAdminProducts_BuyerForbidden_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("Seller gọi API lấy danh sách sản phẩm admin bị 403 Forbidden")
    void getAdminProducts_SellerForbidden_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("Admin lấy danh sách sản phẩm thành công trả về 200 OK và PageResponse")
    void getAdminProducts_Admin_ShouldReturn200() throws Exception {
        AdminProductResponse item = AdminProductResponse.builder()
                .id(100L)
                .name("Tai nghe Bluetooth")
                .slug("tai-nghe-bluetooth")
                .storeId(10L)
                .storeName("Anker Store")
                .categoryId(5)
                .categoryName("Phụ kiện")
                .minPrice(BigDecimal.valueOf(199000))
                .maxPrice(BigDecimal.valueOf(399000))
                .totalStock(50)
                .status("ACTIVE")
                .build();

        PageResponse<AdminProductResponse> pageResponse = PageResponse.<AdminProductResponse>builder()
                .items(List.of(item))
                .pageNumber(0)
                .pageSize(10)
                .totalElements(1)
                .totalPages(1)
                .isFirst(true)
                .isLast(true)
                .build();

        when(productService.getAdminProducts(eq("ACTIVE"), eq(5), eq("Bluetooth"), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/products")
                        .param("status", "ACTIVE")
                        .param("categoryId", "5")
                        .param("search", "Bluetooth")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].id").value(100))
                .andExpect(jsonPath("$.data.items[0].name").value("Tai nghe Bluetooth"))
                .andExpect(jsonPath("$.data.items[0].storeName").value("Anker Store"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("Chưa đăng nhập cập nhật trạng thái sản phẩm trả về 401 Unauthorized")
    void updateProductStatus_Unauthenticated_ShouldReturn401() throws Exception {
        UpdateProductStatusRequest request = new UpdateProductStatusRequest("BANNED", "Vi phạm chính sách");

        mockMvc.perform(patch("/api/v1/admin/products/100/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("Buyer gọi API cập nhật trạng thái sản phẩm bị 403 Forbidden")
    void updateProductStatus_BuyerForbidden_ShouldReturn403() throws Exception {
        UpdateProductStatusRequest request = new UpdateProductStatusRequest("BANNED", "Vi phạm chính sách");

        mockMvc.perform(patch("/api/v1/admin/products/100/status")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("Seller gọi API cập nhật trạng thái sản phẩm bị 403 Forbidden")
    void updateProductStatus_SellerForbidden_ShouldReturn403() throws Exception {
        UpdateProductStatusRequest request = new UpdateProductStatusRequest("BANNED", "Vi phạm chính sách");

        mockMvc.perform(patch("/api/v1/admin/products/100/status")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("Admin cập nhật trạng thái sản phẩm thành công trả về 200 OK")
    void updateProductStatus_Admin_ShouldReturn200() throws Exception {
        UpdateProductStatusRequest request = new UpdateProductStatusRequest("BANNED", "Hàng giả");
        AdminProductResponse response = AdminProductResponse.builder()
                .id(100L)
                .name("Tai nghe Bluetooth")
                .status("BANNED")
                .build();

        when(productService.updateProductStatusForAdmin(eq(100L), any(UpdateProductStatusRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/admin/products/100/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("BANNED"));
    }
}
