package com.b2c.flash_sale_b2c_UTC2.product.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.auth.security.JwtAuthenticationFilter;
import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.config.SecurityConfig;
import com.b2c.flash_sale_b2c_UTC2.product.dto.CreateProductRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductDetailResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductSummaryResponse;
import com.b2c.flash_sale_b2c_UTC2.product.service.ProductService;
import com.b2c.flash_sale_b2c_UTC2.image.service.ImageService;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ProductController.class, SellerProductController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "jwt.secret=MockSecureSecretForTestingJwtAuthenticationFilter2026"
})
class ProductSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private com.b2c.flash_sale_b2c_UTC2.search.service.SearchService searchService;

    @MockitoBean
    private ImageService imageService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("Public xem danh sách sản phẩm không cần token -> 200 OK")
    void getPublicProducts_WithoutToken_ShouldReturn200() throws Exception {
        ProductSummaryResponse summary = ProductSummaryResponse.builder()
                .id(1L)
                .name("Sản phẩm Public")
                .minPrice(new BigDecimal("100000"))
                .maxPrice(new BigDecimal("200000"))
                .totalStock(100)
                .status("ACTIVE")
                .build();

        PageResponse<ProductSummaryResponse> pageResponse = PageResponse.of(new PageImpl<>(List.of(summary)));
        when(productService.getPublicProducts(any(), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].name").value("Sản phẩm Public"));
    }

    @Test
    @DisplayName("Seller endpoint (/api/v1/seller/products) gọi không có token -> 401 Unauthorized")
    void sellerEndpoint_WithoutToken_ShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/seller/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Buyer gọi vào seller endpoint (/api/v1/seller/products) -> 403 Forbidden")
    void sellerEndpoint_WithBuyerRole_ShouldReturn403() throws Exception {
        String token = "buyer_token";
        User buyer = User.builder().id(2L).email("buyer@example.com").build();
        CustomUserDetails userDetails = new CustomUserDetails(buyer, Collections.singletonList(new SimpleGrantedAuthority("ROLE_BUYER")));

        when(jwtService.extractTokenType(token)).thenReturn("ACCESS");
        when(jwtService.extractUsername(token)).thenReturn("buyer@example.com");
        when(userDetailsService.loadUserByUsername("buyer@example.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);

        mockMvc.perform(post("/api/v1/seller/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Seller hợp lệ (ROLE_SELLER) gọi vào seller endpoint -> 201 Created")
    void sellerEndpoint_WithSellerRole_ShouldReturn201() throws Exception {
        String token = "seller_token";
        User seller = User.builder().id(10L).email("seller@example.com").build();
        CustomUserDetails userDetails = new CustomUserDetails(seller, Collections.singletonList(new SimpleGrantedAuthority("ROLE_SELLER")));

        when(jwtService.extractTokenType(token)).thenReturn("ACCESS");
        when(jwtService.extractUsername(token)).thenReturn("seller@example.com");
        when(userDetailsService.loadUserByUsername("seller@example.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);

        CreateProductRequest request = CreateProductRequest.builder()
                .categoryId(1)
                .name("Sản phẩm mới")
                .variants(List.of(
                        com.b2c.flash_sale_b2c_UTC2.product.dto.CreateProductVariantRequest.builder()
                                .sku("SKU-SELLER-100")
                                .variantName("Mặc định")
                                .originalPrice(new BigDecimal("100000"))
                                .stockQuantity(10)
                                .build()
                ))
                .build();

        ProductDetailResponse response = ProductDetailResponse.builder()
                .id(100L)
                .name("Sản phẩm mới")
                .status("ACTIVE")
                .build();

        when(productService.createProduct(eq(10L), any(CreateProductRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/seller/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100));
    }
}
