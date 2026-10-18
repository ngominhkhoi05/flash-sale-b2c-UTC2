package com.b2c.flash_sale_b2c_UTC2.review.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.auth.security.JwtAuthenticationFilter;
import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.config.SecurityConfig;
import com.b2c.flash_sale_b2c_UTC2.review.dto.ReplyReviewRequest;
import com.b2c.flash_sale_b2c_UTC2.review.dto.SellerReviewItemResponse;
import com.b2c.flash_sale_b2c_UTC2.review.dto.SellerReviewSummaryResponse;
import com.b2c.flash_sale_b2c_UTC2.review.service.ReviewService;
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

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SellerReviewController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "jwt.secret=MockSecureSecretForTestingJwtAuthenticationFilter2026"
})
class SellerReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ReviewService reviewService;

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
    @DisplayName("GET /api/v1/seller/reviews/summary - Thành công cho SELLER")
    void getSummary_Success() throws Exception {
        SellerReviewSummaryResponse summary = SellerReviewSummaryResponse.builder()
                .averageRating(4.8)
                .totalReviews(25L)
                .responseRate(0.92)
                .unrepliedCount(2L)
                .ratingBreakdown(Map.of(5, 20L, 4, 5L))
                .build();

        when(reviewService.getSellerReviewSummary(20L)).thenReturn(summary);

        mockMvc.perform(get("/api/v1/seller/reviews/summary")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.averageRating").value(4.8))
                .andExpect(jsonPath("$.data.totalReviews").value(25));
    }

    @Test
    @DisplayName("GET /api/v1/seller/reviews - Thành công cho SELLER")
    void getReviews_Success() throws Exception {
        SellerReviewItemResponse item = SellerReviewItemResponse.builder()
                .id(1L)
                .productId(10L)
                .productName("Sản phẩm mẫu")
                .buyerName("Người mua A")
                .rating(5)
                .comment("Rất hài lòng")
                .createdAt(Instant.now())
                .build();

        PageResponse<SellerReviewItemResponse> pageResponse = PageResponse.of(
                new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1)
        );

        when(reviewService.getSellerReviews(eq(20L), any(), any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/seller/reviews")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.items[0].rating").value(5));
    }

    @Test
    @DisplayName("POST /api/v1/seller/reviews/{id}/reply - Thành công cho SELLER")
    void replyReview_Success() throws Exception {
        ReplyReviewRequest request = ReplyReviewRequest.builder()
                .replyText("Cảm ơn bạn đã đánh giá!")
                .build();

        SellerReviewItemResponse response = SellerReviewItemResponse.builder()
                .id(1L)
                .sellerReply(SellerReviewItemResponse.SellerReviewReplyDto.builder()
                        .id(1L)
                        .replyText("Cảm ơn bạn đã đánh giá!")
                        .repliedAt(Instant.now())
                        .build())
                .build();

        when(reviewService.replySellerReview(eq(20L), eq(1L), any(ReplyReviewRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/seller/reviews/1/reply")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sellerReply.replyText").value("Cảm ơn bạn đã đánh giá!"));
    }

    @Test
    @DisplayName("GET /api/v1/seller/reviews - 403 Forbidden cho BUYER")
    void getReviews_Forbidden_ForBuyer() throws Exception {
        mockMvc.perform(get("/api/v1/seller/reviews")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden());
    }
}
