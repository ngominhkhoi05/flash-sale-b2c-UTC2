package com.b2c.flash_sale_b2c_UTC2;

import com.b2c.flash_sale_b2c_UTC2.common.test.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for homepage public endpoints:
 * - GET /api/v1/banners/home
 * - GET /api/v1/stores/featured
 * - GET /api/v1/products/recommended (with tab param)
 *
 * No auth required. Uses Supabase Dev DB via application-test.yaml.
 */
class HomepageApiSecurityIT extends AbstractPostgresIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("GET /api/v1/banners/home - Public endpoint, no auth required")
    void getHomeBanners_NoAuth_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/banners/home")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("GET /api/v1/stores/featured - Public endpoint, no auth required")
    void getFeaturedStores_NoAuth_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/stores/featured")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("GET /api/v1/products/recommended - Public endpoint, no auth required")
    void getRecommendedProducts_NoAuth_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/products/recommended")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("GET /api/v1/products/recommended?tab=best_seller - Tab param works")
    void getRecommendedProducts_WithTabParam_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/products/recommended")
                        .param("tab", "best_seller")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bestSellers").exists());
    }

    @Test
    @DisplayName("GET /api/v1/products/recommended?tab=hot_deal - hot_deal tab")
    void getRecommendedProducts_HotDealTab_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/products/recommended")
                        .param("tab", "hot_deal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.hotDeals").exists());
    }
}
