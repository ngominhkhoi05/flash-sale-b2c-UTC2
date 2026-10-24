package com.b2c.flash_sale_b2c_UTC2.product.controller;

import com.b2c.flash_sale_b2c_UTC2.auth.security.CustomUserDetails;
import com.b2c.flash_sale_b2c_UTC2.auth.security.JwtAuthenticationFilter;
import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import com.b2c.flash_sale_b2c_UTC2.config.JwtProperties;
import com.b2c.flash_sale_b2c_UTC2.config.SecurityConfig;
import com.b2c.flash_sale_b2c_UTC2.product.dto.RecommendedProductResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.SearchRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.SearchResponse;
import com.b2c.flash_sale_b2c_UTC2.product.service.ProductService;
import com.b2c.flash_sale_b2c_UTC2.search.service.SearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test cho {@code GET /api/v1/products/search} (contract cursor m뿯ẽi).
 *
 * <p>Ki뿯ẽm ch뿯ẽng:
 * <ul>
 *   <li>Response c뿯½ đ뿯ẽ field cursor: {@code size}, {@code nextCursor}, {@code hasMore},
 *       {@code totalIsExact} — v뿯½ <b>kh뿯½ng c뿯½n</b> field {@code page}/{@code pageSize}.</li>
 *   <li>Tham s뿯ẽ {@code cursor} đ뿯ƽ뿯ẽc forward xu뿯ẽng {@link SearchService}.</li>
 *   <li>Endpoint public — kh뿯½ng c뿯ẽn token.</li>
 * </ul>
 */
@WebMvcTest(controllers = ProductController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "jwt.secret=MockSecureSecretForTestingJwtAuthenticationFilter2026"
})
class ProductSearchCursorContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private SearchService searchService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("GET /api/v1/products/search -> tra items + size + nextCursor + hasMore + totalIsExact")
    void searchProducts_ShouldReturnCursorContract() throws Exception {
        RecommendedProductResponse item = RecommendedProductResponse.builder()
                .id(7L)
                .name("Tai nghe Bluetooth")
                .minPrice(new BigDecimal("299000"))
                .maxPrice(new BigDecimal("399000"))
                .totalStock(12)
                .totalSold(30)
                .build();

        SearchResponse response = SearchResponse.builder()
                .items(List.of(item))
                .total(120)
                .size(24)
                .nextCursor("payload.signature")
                .hasMore(true)
                .totalIsExact(true)
                .build();

        when(searchService.search(any(SearchRequest.class), isNull())).thenReturn(response);

        mockMvc.perform(get("/api/v1/products/search")
                        .param("q", "tai nghe")
                        .param("sort", "relevance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].id").value(7))
                .andExpect(jsonPath("$.data.total").value(120))
                .andExpect(jsonPath("$.data.size").value(24))
                .andExpect(jsonPath("$.data.nextCursor").value("payload.signature"))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.totalIsExact").value(true))
                // Contract m뿯ẽi b뿯ẽ page/pageSize -> kh뿯½ng đ뿯ƽ뿯ẽc xu뿯ẽt hi뿯ẽn.
                .andExpect(jsonPath("$.data.page").doesNotExist())
                .andExpect(jsonPath("$.data.pageSize").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/products/search?cursor=... -> forward cursor xu뿯ẽng SearchService")
    void searchProducts_ShouldForwardCursorToService() throws Exception {
        when(searchService.search(any(SearchRequest.class), eq("opaque-token")))
                .thenReturn(SearchResponse.builder()
                        .items(List.of())
                        .total(0)
                        .size(24)
                        .nextCursor(null)
                        .hasMore(false)
                        .totalIsExact(true)
                        .build());

        mockMvc.perform(get("/api/v1/products/search")
                        .param("cursor", "opaque-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(searchService).search(requestCaptor.capture(), eq("opaque-token"));
        assertNotNull(requestCaptor.getValue());
    }

    @Test
    @DisplayName("GET /api/v1/products/search -> khong can token (public)")
    void searchProducts_WithoutToken_ShouldReturn200() throws Exception {
        when(searchService.search(any(SearchRequest.class), isNull())).thenReturn(SearchResponse.builder()
                .items(List.of())
                .total(0)
                .size(24)
                .hasMore(false)
                .totalIsExact(true)
                .build());

        mockMvc.perform(get("/api/v1/products/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
