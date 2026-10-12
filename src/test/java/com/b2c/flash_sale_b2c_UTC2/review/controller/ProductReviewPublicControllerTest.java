package com.b2c.flash_sale_b2c_UTC2.review.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.JwtAuthenticationFilter;
import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.config.SecurityConfig;
import com.b2c.flash_sale_b2c_UTC2.review.dto.ProductReviewResponse;
import com.b2c.flash_sale_b2c_UTC2.review.dto.ProductReviewsPageResponse;
import com.b2c.flash_sale_b2c_UTC2.review.dto.RatingDistributionDto;
import com.b2c.flash_sale_b2c_UTC2.review.exception.ReviewErrorCode;
import com.b2c.flash_sale_b2c_UTC2.review.service.ReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductReviewPublicController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProperties.class})
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "jwt.secret=MockSecureSecretForTestingJwtAuthenticationFilter2026"
})
class ProductReviewPublicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("GET /api/v1/products/{id}/reviews - 200 with valid productId")
    void shouldReturn200WithValidProductId() throws Exception {
        RatingDistributionDto dist = RatingDistributionDto.builder()
                .stars5(10L).stars4(5L).stars3(2L).stars2(1L).stars1(0L).build();
        ProductReviewResponse item = ProductReviewResponse.builder()
                .id(1L).rating(5).comment("Great product").userName("Nguyen Van A")
                .variantSku("SKU-001").album(List.of("https://img.com/1.jpg"))
                .createdAt(java.time.Instant.parse("2026-10-03T08:00:00Z")).build();

        ProductReviewsPageResponse pageResponse = ProductReviewsPageResponse.builder()
                .items(List.of(item))
                .ratingDistribution(dist)
                .mediaCount(5L)
                .totalElements(18)
                .totalPages(2)
                .page(0)
                .size(10)
                .build();

        when(reviewService.getProductReviews(eq(1L), any(), any(), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/products/1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.items[0].rating").value(5))
                .andExpect(jsonPath("$.data.items[0].userName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.data.ratingDistribution.stars5").value(10))
                .andExpect(jsonPath("$.data.ratingDistribution.stars4").value(5))
                .andExpect(jsonPath("$.data.ratingDistribution.stars3").value(2))
                .andExpect(jsonPath("$.data.ratingDistribution.stars2").value(1))
                .andExpect(jsonPath("$.data.ratingDistribution.stars1").value(0))
                .andExpect(jsonPath("$.data.mediaCount").value(5))
                .andExpect(jsonPath("$.data.totalElements").value(18))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id}/reviews?rating=5 - 200 with rating filter")
    void shouldReturn200WithRatingFilter() throws Exception {
        RatingDistributionDto dist = RatingDistributionDto.builder()
                .stars5(10L).stars4(5L).stars3(2L).stars2(1L).stars1(0L).build();
        ProductReviewResponse item = ProductReviewResponse.builder()
                .id(2L).rating(5).comment("Five stars").userName("User B").build();

        ProductReviewsPageResponse pageResponse = ProductReviewsPageResponse.builder()
                .items(List.of(item))
                .ratingDistribution(dist)
                .mediaCount(5L)
                .totalElements(10)
                .totalPages(1)
                .page(0)
                .size(10)
                .build();

        when(reviewService.getProductReviews(eq(1L), any(), any(), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/products/1/reviews").param("rating", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].rating").value(5));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id}/reviews?hasMedia=true - 200 with hasMedia filter")
    void shouldReturn200WithHasMediaFilter() throws Exception {
        RatingDistributionDto dist = RatingDistributionDto.builder()
                .stars5(3L).stars4(1L).stars3(0L).stars2(0L).stars1(0L).build();
        ProductReviewsPageResponse pageResponse = ProductReviewsPageResponse.builder()
                .items(Collections.emptyList())
                .ratingDistribution(dist)
                .mediaCount(4L)
                .totalElements(0)
                .totalPages(0)
                .page(0)
                .size(10)
                .build();

        when(reviewService.getProductReviews(eq(1L), any(), any(), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/products/1/reviews").param("hasMedia", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mediaCount").value(4));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id}/reviews - 404 when product not found")
    void shouldReturn404WhenProductNotFound() throws Exception {
        when(reviewService.getProductReviews(eq(999L), any(), any(), any()))
                .thenThrow(new BusinessException(ReviewErrorCode.REVIEW_PRODUCT_NOT_FOUND));

        mockMvc.perform(get("/api/v1/products/999/reviews"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id}/reviews - 200 with pagination params")
    void shouldReturn200WithPaginationParams() throws Exception {
        RatingDistributionDto dist = RatingDistributionDto.builder()
                .stars5(0L).stars4(0L).stars3(0L).stars2(0L).stars1(0L).build();
        ProductReviewsPageResponse pageResponse = ProductReviewsPageResponse.builder()
                .items(Collections.emptyList())
                .ratingDistribution(dist)
                .mediaCount(0L)
                .totalElements(0)
                .totalPages(0)
                .page(2)
                .size(20)
                .build();

        when(reviewService.getProductReviews(eq(1L), any(), any(), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/products/1/reviews")
                        .param("page", "2")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(20));
    }
}
