package com.b2c.flash_sale_b2c_UTC2.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test CORS config tuân thủ W3C spec khi allowCredentials=true.
 *
 * Fix 2026-10-18: spec CORS cấm wildcard "*" cho headers/origins khi
 * credentials=true. CorsConfig đã được sửa để dùng explicit list.
 *
 * Test này verify 5 ràng buộc:
 * 1. Headers không chứa wildcard "*"
 * 2. Headers chứa đủ header FE cần (content-type, x-user-id, authorization)
 * 3. Origins trả về exact match, không phải pattern
 * 4. Env "*" được map thành sentinel "null" (tránh spec violation)
 * 5. Allow credentials = true
 */
class CorsConfigTest {

    private CorsConfig corsConfig;

    @BeforeEach
    void setUp() {
        corsConfig = new CorsConfig();
    }

    @Test
    @DisplayName("Headers khong chua wildcard '*' khi credentials=true")
    void headersShouldNotContainWildcard() {
        ReflectionTestUtils.setField(corsConfig,
            "allowedOriginsRaw", "https://example.com", String.class);
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        CorsConfiguration config = source.getCorsConfiguration(
            new MockHttpServletRequest());

        List<String> headers = config.getAllowedHeaders();
        assertNotNull(headers, "Allowed headers khong duoc null");
        assertFalse(headers.contains("*"),
            "Spec W3C cam wildcard '*' trong Allow-Headers khi credentials=true");
    }

    @Test
    @DisplayName("Headers chua content-type, x-user-id, authorization (FE can)")
    void headersShouldContainRequiredForFE() {
        ReflectionTestUtils.setField(corsConfig,
            "allowedOriginsRaw", "https://example.com", String.class);
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        CorsConfiguration config = source.getCorsConfiguration(
            new MockHttpServletRequest());

        List<String> headers = config.getAllowedHeaders();
        assertTrue(headers.contains("content-type"),
            "FE POST JSON can content-type header");
        assertTrue(headers.contains("x-user-id"),
            "FE apiFetch gui X-User-Id custom header");
        assertTrue(headers.contains("authorization"),
            "JWT bearer token can authorization header");
        assertTrue(headers.contains("x-idempotency-key"),
            "Idempotency-Key wrapper can header nay");
        assertTrue(headers.contains("accept"),
            "FE apiFetch gui Accept header");
    }

    @Test
    @DisplayName("Origins tra ve exact match, khong phai pattern")
    void originsShouldBeExactNotPattern() {
        ReflectionTestUtils.setField(corsConfig,
            "allowedOriginsRaw", "https://example.com", String.class);
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        CorsConfiguration config = source.getCorsConfiguration(
            new MockHttpServletRequest());

        List<String> origins = config.getAllowedOrigins();
        assertNotNull(origins, "Allowed origins khong duoc null");
        assertTrue(origins.contains("https://example.com"),
            "Origin phai duoc echo exact");
    }

    @Test
    @DisplayName("Env CORS_ALLOWED_ORIGINS=* duoc map thanh 'null' sentinel")
    void wildcardEnvShouldBecomeNullSentinel() {
        ReflectionTestUtils.setField(corsConfig,
            "allowedOriginsRaw", "*", String.class);
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        CorsConfiguration config = source.getCorsConfiguration(
            new MockHttpServletRequest());

        // Dac biet: setAllowedOriginPatterns(["null"]) thay vi ["*"]
        // de tranh spec violation khi credentials=true.
        List<String> patterns = config.getAllowedOriginPatterns();
        assertNotNull(patterns, "Origin patterns khong duoc null");
        assertTrue(patterns.contains("null"),
            "Wildcard env phai duoc map thanh 'null' sentinel");
        assertFalse(patterns.contains("*"),
            "KHONG bao gio cho phep pattern '*' khi credentials=true");
    }

    @Test
    @DisplayName("Allow credentials = true (JWT cookie auth can)")
    void credentialsShouldBeTrue() {
        ReflectionTestUtils.setField(corsConfig,
            "allowedOriginsRaw", "https://example.com", String.class);
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        CorsConfiguration config = source.getCorsConfiguration(
            new MockHttpServletRequest());

        assertTrue(config.getAllowCredentials(),
            "Credentials phai la true de JWT cookie hoat dong");
    }
}
