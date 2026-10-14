package com.b2c.flash_sale_b2c_UTC2.config.realtime;

import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

/**
 * Interceptor chạy trong phase FIRST tại HTTP WebSocket handshake.
 *
 * <p>
 * Đọc JWT từ query param {@code ?token=<jwt>} (vì browser không set được
 * {@code Authorization} header khi upgrade WebSocket), validate token,
 * và gán Principal vào WebSocketSession attributes.
 * Principal này sau đó được ChannelInterceptor copy vào STOMP headers.
 * </p>
 *
 * <p>
 * <b>Không xử lý topic public</b> — ai connect được thì subscribe public topic
 * đều nhận message. Chỉ xác thực khi cần phân quyền cụ thể hoặc gửi
 * message riêng tới user qua {@code /user/queue/...}.
 * </p>
 *
 * @see WebSocketConfig
 * @see WebSocketAuthConfig
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HandshakeAuthInterceptor implements HandshakeInterceptor {

    private static final String ATTR_USER = "ws.auth.user";
    private static final String PARAM_TOKEN = "token";

    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes
    ) {
        String token = extractToken(request);

        if (token == null || token.isBlank()) {
            // Không có token → cho phép connect nhưng không có principal
            // (sẽ bị chặn ở topic private bởi Spring STOMP)
            log.debug("WebSocket handshake without token — public access only");
            return true;
        }

        try {
            String username = jwtService.extractUsername(token);

            if (username != null && !jwtService.isTokenExpired(token)) {
                // Tạo principal tạm để lưu vào session attributes
                UserDetails principal = User.builder()
                        .username(username)
                        .password("")
                        .authorities(extractAuthorities())
                        .build();

                attributes.put(ATTR_USER, principal);
                log.debug("WebSocket handshake authenticated for user: {}", username);
                return true;
            }

            log.warn("WebSocket handshake — invalid JWT token");
            return false;
        } catch (Exception e) {
            log.warn("WebSocket handshake — JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            Exception exception
    ) {
        // Không cần xử lý gì sau handshake
    }

    /**
     * Trích JWT từ query param (?token=xxx).
     * WS handshake chỉ hỗ trợ query param, không hỗ trợ header.
     */
    private String extractToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return servletRequest.getServletRequest().getParameter(PARAM_TOKEN);
        }
        // Fallback: đọc từ query string
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith(PARAM_TOKEN + "=")) {
                    return param.substring(PARAM_TOKEN.length() + 1);
                }
            }
        }
        return null;
    }

    /**
     * Default role cho user authenticated qua WS.
     * Chi tiết authorities sẽ được load lại trong ChannelInterceptor
     * qua {@link org.springframework.security.core.userdetails.UserDetailsService}.
     */
    private List<SimpleGrantedAuthority> extractAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_BUYER"));
    }
}
