package com.b2c.flash_sale_b2c_UTC2.order.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.auth.security.JwtAuthenticationFilter;
import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.config.SecurityConfig;
import com.b2c.flash_sale_b2c_UTC2.order.dto.CheckoutItemRequest;
import com.b2c.flash_sale_b2c_UTC2.order.dto.CheckoutStoreOrderRequest;
import com.b2c.flash_sale_b2c_UTC2.order.dto.CreateOrderRequest;
import com.b2c.flash_sale_b2c_UTC2.order.dto.OrderResponse;
import com.b2c.flash_sale_b2c_UTC2.order.dto.QrOrderDetailResponse;
import com.b2c.flash_sale_b2c_UTC2.order.dto.QrShopOrderResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {OrderController.class, SellerOrderController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "jwt.secret=MockSecureSecretForTestingJwtAuthenticationFilter2026"
})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private OrderService orderService;

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

        // Mock Buyer Token
        when(jwtService.extractTokenType(buyerToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(buyerToken)).thenReturn("buyer@example.com");
        when(userDetailsService.loadUserByUsername("buyer@example.com")).thenReturn(buyerDetails);
        when(jwtService.isTokenValid(buyerToken, buyerDetails)).thenReturn(true);

        // Mock Seller Token
        when(jwtService.extractTokenType(sellerToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(sellerToken)).thenReturn("seller@example.com");
        when(userDetailsService.loadUserByUsername("seller@example.com")).thenReturn(sellerDetails);
        when(jwtService.isTokenValid(sellerToken, sellerDetails)).thenReturn(true);
    }

    @Test
    @DisplayName("Checkout không có token -> 401 Unauthorized")
    void checkout_WithoutToken_ShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Buyer checkout thành công -> 201 Created")
    void checkout_WithBuyerToken_ShouldReturn201() throws Exception {
        CheckoutItemRequest item = CheckoutItemRequest.builder().variantId(500L).quantity(2).build();
        CheckoutStoreOrderRequest storeOrder = CheckoutStoreOrderRequest.builder()
                .storeId(10L)
                .items(List.of(item))
                .build();
        CreateOrderRequest request = CreateOrderRequest.builder()
                .shippingAddressId(100L)
                .storeOrders(List.of(storeOrder))
                .build();

        OrderResponse response = OrderResponse.builder()
                .id(1000L)
                .orderCode("ORD-1234")
                .totalAmount(new BigDecimal("300000.00"))
                .build();

        when(orderService.createOrders(eq(1L), any(CreateOrderRequest.class))).thenReturn(List.of(response));

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].orderCode").value("ORD-1234"));
    }

    @Test
    @DisplayName("Buyer xem danh sách đơn hàng của mình -> 200 OK")
    void getMyOrders_WithBuyerToken_ShouldReturn200() throws Exception {
        PageResponse<OrderResponse> pageResponse = PageResponse.of(new PageImpl<>(List.of(
                OrderResponse.builder().id(1000L).orderCode("ORD-1").build()
        )));

        when(orderService.getBuyerOrders(eq(1L), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].id").value(1000L));
    }

    @Test
    @DisplayName("Buyer xem chi tiết 1 đơn hàng -> 200 OK")
    void getOrderDetail_WithBuyerToken_ShouldReturn200() throws Exception {
        OrderResponse response = OrderResponse.builder().id(1000L).orderCode("ORD-1").build();
        when(orderService.getBuyerOrderDetail(1L, 1000L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/1000")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderCode").value("ORD-1"));
    }

    @Test
    @DisplayName("Buyer hủy đơn hàng -> 200 OK")
    void cancelOrder_WithBuyerToken_ShouldReturn200() throws Exception {
        OrderResponse response = OrderResponse.builder().id(1000L).status("CANCELLED").build();
        when(orderService.cancelOrder(1L, 1000L)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/orders/1000/cancel")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("Buyer cố gọi API seller (/api/v1/seller/orders) -> 403 Forbidden")
    void getSellerOrders_WithBuyerRole_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/seller/orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Seller xem danh sách đơn hàng shop mình -> 200 OK")
    void getSellerOrders_WithSellerRole_ShouldReturn200() throws Exception {
        PageResponse<OrderResponse> pageResponse = PageResponse.of(new PageImpl<>(List.of(
                OrderResponse.builder().id(1000L).orderCode("ORD-SELLER-1").build()
        )));

        when(orderService.getSellerOrders(eq(2L), any(), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/seller/orders")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].orderCode").value("ORD-SELLER-1"));
    }

    @Test
    @DisplayName("Seller cập nhật trạng thái đơn hàng -> 200 OK")
    void updateOrderStatus_WithSellerRole_ShouldReturn200() throws Exception {
        UpdateOrderStatusRequest request = UpdateOrderStatusRequest.builder().status("SHIPPED").build();
        OrderResponse response = OrderResponse.builder().id(1000L).status("SHIPPED").build();

        when(orderService.updateOrderStatusBySeller(eq(2L), eq(1000L), any(UpdateOrderStatusRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/seller/orders/1000/status")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SHIPPED"));
    }

    @Test
    @DisplayName("Buyer lấy mã QR thanh toán -> 200 OK")
    void getOrderQrDetail_WithBuyerToken_ShouldReturn200() throws Exception {
        QrShopOrderResponse shopOrder = QrShopOrderResponse.builder()
                .storeId(10L)
                .storeName("Shop ABC")
                .storeBadge("OfficialMall")
                .orderCode("FS-261001-A7K2Q")
                .amount(new BigDecimal("199000"))
                .itemSummary("Áo thun nam × 2, Quần dài × 1")
                .build();

        QrOrderDetailResponse response = QrOrderDetailResponse.builder()
                .orderCode("FS-261001-A7K2Q")
                .qrCodeUrl("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...")
                .amount(new BigDecimal("199000"))
                .totalAmount(new BigDecimal("199000"))
                .shopOrders(List.of(shopOrder))
                .build();

        when(orderService.getOrderQrDetail(1L, "FS-261001-A7K2Q")).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/FS-261001-A7K2Q/qr")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderCode").value("FS-261001-A7K2Q"))
                .andExpect(jsonPath("$.data.amount").value(199000))
                .andExpect(jsonPath("$.data.totalAmount").value(199000))
                .andExpect(jsonPath("$.data.shopOrders[0].storeName").value("Shop ABC"))
                .andExpect(jsonPath("$.data.shopOrders[0].storeBadge").value("OfficialMall"))
                .andExpect(jsonPath("$.data.shopOrders[0].itemSummary").value("Áo thun nam × 2, Quần dài × 1"));
    }

    @Test
    @DisplayName("Lấy mã QR không có token -> 401 hoặc 403")
    void getOrderQrDetail_WithoutToken_ShouldReturnUnauthorizedOrForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/orders/FS-261001-A7K2Q/qr")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }
}
