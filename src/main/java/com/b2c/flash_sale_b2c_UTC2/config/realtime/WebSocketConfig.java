package com.b2c.flash_sale_b2c_UTC2.config.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Cấu hình WebSocket với STOMP + SockJS cho realtime Flash Sale stock push.
 *
 * <ul>
 *   <li>Endpoint handshake: {@code /ws} (HTTP upgrade)</li>
 *   <li>Broker prefix public: {@code /topic} (broadcast stock, slot status)</li>
 *   <li>Broker prefix private: {@code /user} (order result cho riêng user)</li>
 * </ul>
 *
 * <p>
 * <b>Lưu ý về TaskScheduler:</b> Dùng {@link ObjectProvider} để lookup
 * {@code messageBrokerTaskScheduler} (default bean do {@code @EnableWebSocketMessageBroker}
 * tạo ra) một cách lazy. Tránh circular dependency giữa {@code WebSocketConfig}
 * và {@code DelegatingWebSocketMessageBrokerConfiguration} nếu inject trực tiếp
 * qua constructor.
 * </p>
 *
 * JWT auth được xử lý trong {@link WebSocketAuthConfig} thông qua
 * {@code ?token=<jwt>} query param tại handshake (browser không set được
 * {@code Authorization} header khi upgrade WebSocket).
 *
 * @see WebSocketAuthConfig
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final HandshakeAuthInterceptor handshakeAuthInterceptor;
    private final ObjectProvider<TaskScheduler> taskSchedulerProvider;

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
        // Prefix cho messages gửi từ server → client (subscribe)
        // /topic/flash-sale/slot/1/stock-update  → client subscribe
        // /user/queue/orders/{orderCode}/updates   → private cho từng user
        TaskScheduler scheduler = taskSchedulerProvider.getIfAvailable();
        registry.enableSimpleBroker("/topic", "/user")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(scheduler);

        // Prefix cho messages gửi từ client → server (@MessageMapping)
        // Client gửi:  /app/flash-sale.reservation
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        // HTTP endpoint handshake — ai cũng được connect, auth xử lý trong interceptor
        registry.addEndpoint("/ws")
                // Đăng ký HandshakeInterceptor để đọc JWT từ ?token= query param
                .addInterceptors(handshakeAuthInterceptor)
                .setAllowedOriginPatterns("*")
                // SockJS fallback cho browser/server proxy không hỗ trợ raw WS
                .withSockJS()
                // CORS cho SockJS (set trong CorsConfig cho /ws/**)
                .setDisconnectDelay(30_000);
    }
}
