package com.b2c.flash_sale_b2c_UTC2.config;

import com.b2c.flash_sale_b2c_UTC2.auth.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Map;

/**
 * Cấu hình bảo mật hoàn chỉnh: Stateless Session, JWT Filter, BCrypt và RBAC.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            Map<String, Object> body = Map.of(
                                    "success", false,
                                    "code", HttpStatus.UNAUTHORIZED.value(),
                                    "message", "Chưa xác thực hoặc token không hợp lệ",
                                    "timestamp", java.time.Instant.now().toString()
                            );
                            response.getWriter().write(objectMapper.writeValueAsString(body));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            Map<String, Object> body = Map.of(
                                    "success", false,
                                    "code", HttpStatus.FORBIDDEN.value(),
                                    "message", "Không có quyền truy cập tài nguyên yêu cầu",
                                    "timestamp", java.time.Instant.now().toString()
                            );
                            response.getWriter().write(objectMapper.writeValueAsString(body));
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // WebSocket STOMP handshake endpoint (auth xử lý trong HandshakeInterceptor)
                        .requestMatchers("/ws/**").permitAll()
                        // CORS preflight: phải permitAll TRƯỚC tất cả rule khác.
                        // Nếu thiếu, Spring Security trả 403 cho OPTIONS → browser block mọi
                        // cross-origin request có body/header khác "simple".
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        // Public endpoints: Auth APIs
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Public endpoints: Xem công khai Store, Category, Product
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/stores/{id:[0-9]+}").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/products/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/flash-sales/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/banners/**").permitAll()
                        // Seller endpoints: yêu cầu vai trò SELLER
                        .requestMatchers("/api/v1/seller/**").hasRole("SELLER")
                        // Admin endpoints: yêu cầu vai trò ADMIN
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // Public endpoints: Swagger UI & OpenAPI Docs
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/v3/api-docs",
                                "/error"
                        ).permitAll()
                        // Actuator health/info — Docker healthcheck gọi không có JWT.
                        // Phải permitAll, nếu không /actuator/health trả 401 →
                        // container luôn unhealthy → nginx (depends_on: service_healthy)
                        // không khởi động → toàn bộ stack sập.
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // Tất cả các request còn lại bắt buộc phải xác thực qua JWT
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
