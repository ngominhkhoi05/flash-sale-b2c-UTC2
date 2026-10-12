package com.b2c.flash_sale_b2c_UTC2.cart.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.auth.security.JwtAuthenticationFilter;
import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import com.b2c.flash_sale_b2c_UTC2.cart.dto.AddToCartRequest;
import com.b2c.flash_sale_b2c_UTC2.cart.dto.CartResponse;
import com.b2c.flash_sale_b2c_UTC2.cart.dto.UpdateCartItemRequest;
import com.b2c.flash_sale_b2c_UTC2.cart.service.CartService;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.config.SecurityConfig;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CartController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "jwt.secret=MockSecureSecretForTestingJwtAuthenticationFilter2026"
})
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private CustomUserDetails buyerDetails;
    private final String buyerToken = "valid_buyer_token";

    @BeforeEach
    void setUp() {
        User buyer = User.builder().id(1L).email("buyer@example.com").build();
        buyerDetails = new CustomUserDetails(buyer, Collections.singletonList(new SimpleGrantedAuthority("ROLE_BUYER")));

        when(jwtService.extractTokenType(buyerToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(buyerToken)).thenReturn("buyer@example.com");
        when(userDetailsService.loadUserByUsername("buyer@example.com")).thenReturn(buyerDetails);
        when(jwtService.isTokenValid(buyerToken, buyerDetails)).thenReturn(true);
    }

    @Test
    @DisplayName("Xem giỏ hàng không có token -> 401 Unauthorized")
    void getCart_WithoutToken_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Xem giỏ hàng với token Buyer hợp lệ -> 200 OK")
    void getCart_WithValidToken_ShouldReturn200() throws Exception {
        CartResponse cartResponse = CartResponse.builder()
                .id(10L)
                .storeGroups(List.of())
                .grandTotal(BigDecimal.ZERO)
                .build();

        when(cartService.getCart(1L)).thenReturn(cartResponse);

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10L));
    }

    @Test
    @DisplayName("Thêm sản phẩm vào giỏ hàng với token Buyer -> 201 Created")
    void addToCart_Success_ShouldReturn201() throws Exception {
        AddToCartRequest request = AddToCartRequest.builder().variantId(500L).quantity(2).build();
        CartResponse cartResponse = CartResponse.builder().id(10L).grandTotal(new BigDecimal("300000.00")).build();

        when(cartService.addToCart(eq(1L), any(AddToCartRequest.class))).thenReturn(cartResponse);

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.grandTotal").value(300000.00));
    }

    @Test
    @DisplayName("Cập nhật số lượng item giỏ hàng -> 200 OK")
    void updateCartItem_Success_ShouldReturn200() throws Exception {
        UpdateCartItemRequest request = UpdateCartItemRequest.builder().quantity(5).build();
        CartResponse cartResponse = CartResponse.builder().id(10L).grandTotal(new BigDecimal("750000.00")).build();

        when(cartService.updateCartItem(eq(1L), eq(100L), any(UpdateCartItemRequest.class))).thenReturn(cartResponse);

        mockMvc.perform(put("/api/v1/cart/items/100")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Xóa một sản phẩm khỏi giỏ hàng -> 200 OK")
    void removeCartItem_Success_ShouldReturn200() throws Exception {
        mockMvc.perform(delete("/api/v1/cart/items/100")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(cartService).removeCartItem(1L, 100L);
    }

    @Test
    @DisplayName("Xóa toàn bộ giỏ hàng -> 200 OK")
    void clearCart_Success_ShouldReturn200() throws Exception {
        mockMvc.perform(delete("/api/v1/cart")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(cartService).clearCart(1L);
    }
}
