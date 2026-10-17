package com.b2c.flash_sale_b2c_UTC2.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * Cấu hình chia sẻ tài nguyên nguồn gốc chéo (CORS) cho phép Storefront Next.js và Admin kết nối.
 *
 * Origins được đọc từ env var {@code cors.allowed-origins} (comma-separated).
 * Mặc định local/dev: http://localhost:3000, http://localhost:3001, http://127.0.0.1:3000
 * Production: set trong application-prod.yaml → biến CORS_ALLOWED_ORIGINS.
 *
 * Không load trong test profile — test dùng TestCorsConfig riêng.
 */
@Configuration
@Profile("!test & !test-local")
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://127.0.0.1:3000}")
    private String allowedOriginsRaw;

    private List<String> getAllowedOrigins() {
        return Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = getAllowedOrigins();

        // Fix 2026-10-18: wildcard origin pattern KHONG hop le khi
        // allowCredentials=true (spec CORS cam). Phai echo origin cu the.
        // Tu dong chuyen "*" -> "null" (KHONG BAO GIO dung "*" khi co credentials).
        boolean hasWildcard = origins.size() == 1 && "*".equals(origins.get(0));
        if (hasWildcard) {
            configuration.setAllowedOriginPatterns(List.of("null"));
        } else {
            configuration.setAllowedOrigins(origins);
        }

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Fix 2026-10-18: wildcard header KHONG hop le khi allowCredentials=true.
        // Browser gui Access-Control-Request-Headers cho preflight POST co body JSON.
        // Phai list explicit theo spec W3C CORS (lowercase, comma-separated).
        configuration.setAllowedHeaders(List.of(
            "content-type", "authorization", "x-user-id", "x-idempotency-key",
            "accept", "origin", "referer", "user-agent"
        ));
        configuration.setExposedHeaders(List.of("Authorization", "X-Request-Id", "Idempotency-Key"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = getAllowedOrigins();
        boolean hasWildcard = origins.size() == 1 && "*".equals(origins.get(0));
        String[] originArray = hasWildcard
            ? new String[]{"null"}
            : origins.toArray(new String[0]);

        registry.addMapping("/**")
                .allowedOrigins(originArray)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders(
                    "content-type", "authorization", "x-user-id", "x-idempotency-key",
                    "accept", "origin", "referer", "user-agent"
                )
                .exposedHeaders("Authorization", "X-Request-Id", "Idempotency-Key")
                .allowCredentials(true)
                .maxAge(3600);

        // WebSocket SockJS transport cần riêng CORS config
        // (SockJS dùng iframe + xhr-streaming có thể trigger preflight)
        registry.addMapping("/ws/**")
                .allowedOrigins(originArray)
                .allowedMethods("GET", "POST")
                .allowedHeaders(
                    "content-type", "authorization", "x-user-id", "x-idempotency-key",
                    "accept", "origin", "referer", "user-agent"
                )
                .allowCredentials(true)
                .maxAge(3600);
    }
}
