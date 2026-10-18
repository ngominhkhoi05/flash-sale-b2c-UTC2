package com.b2c.flash_sale_b2c_UTC2.flashsale.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.auth.security.JwtAuthenticationFilter;
import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.config.SecurityConfig;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.FlashSaleItemResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.RegisterFlashSaleItemRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleItemService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SellerFlashSaleController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "jwt.secret=MockSecureSecretForTestingJwtAuthenticationFilter2026"
})
class SellerFlashSaleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private FlashSaleItemService itemService;

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
    @DisplayName("POST /api/v1/seller/flash-sales/items - Success for SELLER")
    void registerItem_Success() throws Exception {
        RegisterFlashSaleItemRequest request = new RegisterFlashSaleItemRequest();
        request.setSlotId(1L);
        request.setVariantId(10L);
        request.setFlashSalePrice(BigDecimal.valueOf(80000));
        request.setAllocatedStock(20);
        request.setUserPurchaseLimit(2);

        FlashSaleItemResponse response = FlashSaleItemResponse.builder()
                .id(100L)
                .slotId(1L)
                .variantId(10L)
                .status("PENDING_APPROVAL")
                .build();

        when(itemService.registerItem(eq(20L), any(RegisterFlashSaleItemRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/seller/flash-sales/items")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("GET /api/v1/seller/flash-sales/items - Success for SELLER")
    void getMyItems_Success() throws Exception {
        FlashSaleItemResponse item = FlashSaleItemResponse.builder()
                .id(100L)
                .slotId(1L)
                .variantId(10L)
                .status("PENDING_APPROVAL")
                .build();

        PageResponse<FlashSaleItemResponse> pageResponse = PageResponse.of(
                new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1)
        );

        when(itemService.getSellerItems(eq(20L), any(), any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/seller/flash-sales/items")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].id").value(100));
    }

    @Test
    @DisplayName("DELETE /api/v1/seller/flash-sales/items/{id} - Success for SELLER")
    void cancelRegistration_Success() throws Exception {
        doNothing().when(itemService).cancelRegistration(eq(20L), eq(100L));

        mockMvc.perform(delete("/api/v1/seller/flash-sales/items/100")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/seller/flash-sales/items - 403 Forbidden for BUYER")
    void getMyItems_Forbidden_ForBuyer() throws Exception {
        mockMvc.perform(get("/api/v1/seller/flash-sales/items")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden());
    }
}
