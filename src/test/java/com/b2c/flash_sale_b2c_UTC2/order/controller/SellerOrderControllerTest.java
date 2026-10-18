package com.b2c.flash_sale_b2c_UTC2.order.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.auth.security.JwtAuthenticationFilter;
import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.config.SecurityConfig;
import com.b2c.flash_sale_b2c_UTC2.order.dto.OrderResponse;
import com.b2c.flash_sale_b2c_UTC2.order.dto.UpdateOrderStatusRequest;
import com.b2c.flash_sale_b2c_UTC2.order.service.OrderService;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
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
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SellerOrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "jwt.secret=MockSecureSecretForTestingJwtAuthenticationFilter2026"
})
class SellerOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private OrderService orderService;

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
    @DisplayName("GET /api/v1/seller/orders - Thành công cho SELLER")
    void getMyStoreOrders_Success() throws Exception {
        OrderResponse order = OrderResponse.builder()
                .id(100L)
                .orderCode("ORD-100")
                .totalAmount(new BigDecimal("250000.00"))
                .status("PENDING_PAYMENT")
                .build();

        PageResponse<OrderResponse> pageResponse = PageResponse.of(
                new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1)
        );

        when(orderService.getSellerOrders(eq(20L), any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/seller/orders")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].orderCode").value("ORD-100"));
    }

    @Test
    @DisplayName("GET /api/v1/seller/orders/{id} - Thành công cho SELLER")
    void getOrderDetail_Success() throws Exception {
        OrderResponse order = OrderResponse.builder()
                .id(100L)
                .orderCode("ORD-100")
                .totalAmount(new BigDecimal("250000.00"))
                .status("COMPLETED")
                .build();

        when(orderService.getSellerOrderDetail(20L, 100L)).thenReturn(order);

        mockMvc.perform(get("/api/v1/seller/orders/100")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.orderCode").value("ORD-100"));
    }

    @Test
    @DisplayName("PATCH /api/v1/seller/orders/{id}/status - Thành công cho SELLER")
    void updateOrderStatus_Success() throws Exception {
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus("SHIPPED");

        OrderResponse order = OrderResponse.builder()
                .id(100L)
                .status("SHIPPED")
                .build();

        when(orderService.updateOrderStatusBySeller(eq(20L), eq(100L), any(UpdateOrderStatusRequest.class)))
                .thenReturn(order);

        mockMvc.perform(patch("/api/v1/seller/orders/100/status")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SHIPPED"));
    }

    @Test
    @DisplayName("GET /api/v1/seller/orders - 403 Forbidden cho BUYER")
    void getMyStoreOrders_Forbidden_ForBuyer() throws Exception {
        mockMvc.perform(get("/api/v1/seller/orders")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden());
    }
}
