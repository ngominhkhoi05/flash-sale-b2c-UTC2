package com.b2c.flash_sale_b2c_UTC2.flashsale.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.auth.security.JwtAuthenticationFilter;
import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.config.SecurityConfig;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.BatchApproveFlashSaleItemsRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.BatchApproveFlashSaleItemsResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.FlashSaleItemResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.RejectFlashSaleItemRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleItemService;
import com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleSlotService;
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

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminFlashSaleController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "jwt.secret=MockSecureSecretForTestingJwtAuthenticationFilter2026"
})
class AdminFlashSaleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private FlashSaleSlotService slotService;

    @MockitoBean
    private FlashSaleItemService itemService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private final String adminToken = "valid_admin_token";
    private final String sellerToken = "valid_seller_token";

    private CustomUserDetails adminDetails;
    private CustomUserDetails sellerDetails;

    @BeforeEach
    void setUp() {
        User adminUser = User.builder().id(1L).email("admin@test.com").build();
        adminDetails = new CustomUserDetails(
                adminUser,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        User sellerUser = User.builder().id(20L).email("seller@test.com").build();
        sellerDetails = new CustomUserDetails(
                sellerUser,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_SELLER"))
        );

        when(jwtService.extractTokenType(adminToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(adminToken)).thenReturn("admin@test.com");
        when(jwtService.isTokenValid(eq(adminToken), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("admin@test.com")).thenReturn(adminDetails);

        when(jwtService.extractTokenType(sellerToken)).thenReturn("ACCESS");
        when(jwtService.extractUsername(sellerToken)).thenReturn("seller@test.com");
        when(jwtService.isTokenValid(eq(sellerToken), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("seller@test.com")).thenReturn(sellerDetails);
    }

    @Test
    @DisplayName("GET /api/v1/admin/flash-sales/slots/{slotId}/items - Success for ADMIN")
    void getSlotItems_Success() throws Exception {
        FlashSaleItemResponse item = FlashSaleItemResponse.builder()
                .id(101L)
                .slotId(5L)
                .variantId(12L)
                .status("PENDING_APPROVAL")
                .build();

        PageResponse<FlashSaleItemResponse> pageResponse = PageResponse.of(
                new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1)
        );

        when(itemService.getAdminSlotItems(eq(5L), any(), any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/flash-sales/slots/5/items")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].id").value(101));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/flash-sales/items/{id}/reject - Success for ADMIN")
    void rejectItem_Success() throws Exception {
        RejectFlashSaleItemRequest request = new RejectFlashSaleItemRequest();
        request.setReason("Discount not attractive enough");

        FlashSaleItemResponse response = FlashSaleItemResponse.builder()
                .id(101L)
                .status("REJECTED")
                .build();

        when(itemService.rejectItem(eq(101L), any(RejectFlashSaleItemRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/v1/admin/flash-sales/items/101/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/flash-sales/items/batch-approve - Success for ADMIN")
    void batchApproveItems_Success() throws Exception {
        BatchApproveFlashSaleItemsRequest request = new BatchApproveFlashSaleItemsRequest();
        request.setItemIds(List.of(101L, 102L));

        BatchApproveFlashSaleItemsResponse response = BatchApproveFlashSaleItemsResponse.builder()
                .approvedItemIds(List.of(101L, 102L))
                .failedReasons(List.of())
                .build();

        when(itemService.batchApproveItems(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/flash-sales/items/batch-approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.approvedItemIds[0]").value(101));
    }

    @Test
    @DisplayName("GET /api/v1/admin/flash-sales/slots/5/items - 403 Forbidden for SELLER")
    void getSlotItems_Forbidden_ForSeller() throws Exception {
        mockMvc.perform(get("/api/v1/admin/flash-sales/slots/5/items")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isForbidden());
    }
}
