package com.b2c.flash_sale_b2c_UTC2.config.realtime;

import com.b2c.flash_sale_b2c_UTC2.auth.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * Cấu hình authentication cho WebSocket STOMP.
 *
 * <p>
 * Browser không set được {@code Authorization} header khi upgrade WebSocket,
 * nên JWT được truyền qua query param: {@code /ws?token=<jwt>}.
 * HandshakeInterceptor (Phase FIRST) đọc query param, validate token,
 * và lưu principal vào session attributes.
 * ChannelInterceptor (Phase PRE) copy principal từ session → STOMP headers.
 * </p>
 *
 * <p>
 * <b>Lưu ý security:</b>
 * <ul>
 *   <li>Topic public (stock, slot status) → ai cũng subscribe được sau khi connect.</li>
 *   <li>Topic private {@code /user/queue/...} → Spring STOMP tự kiểm tra principal
 *       match với user payload để tránh user A nhận message của user B.</li>
 * </ul>
 * </p>
 *
 * @see WebSocketConfig
 * @see HandshakeAuthInterceptor
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebSocketAuthConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // Principal đã được gán trong HandshakeAuthInterceptor
                    if (accessor.getUser() != null) {
                        String username = accessor.getUser().getName();
                        log.debug("WebSocket STOMP CONNECT for user: {}", username);

                        // Reload UserDetails để có authorities đầy đủ (RBAC)
                        // (handshake chỉ set principal tạm với ROLE_BUYER default)
                        try {
                            accessor.setUser(authenticationToken(username));
                        } catch (Exception e) {
                            // User không tồn tại trong DB → giữ principal tạm
                            log.warn("Could not reload UserDetails for WS user: {}. Using handshake principal.", username);
                        }
                    }
                }
                return message;
            }
        });
    }

    /**
     * Tạo Authentication token từ username (đã validate JWT ở handshake).
     * Lấy authorities từ UserDetailsService để giữ RBAC.
     */
    private UsernamePasswordAuthenticationToken authenticationToken(String username) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
    }
}
