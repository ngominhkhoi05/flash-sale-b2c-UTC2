package com.b2c.flash_sale_b2c_UTC2.config.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Listener log các WebSocket STOMP session lifecycle events.
 *
 * <p>
 * Dùng cho mục đích monitoring & debugging.
 * Không chứa business logic.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsSessionEventListener {

    private final AtomicInteger activeSessions = new AtomicInteger(0);

    // Theo dõi active sessions (đơn giản, không persist)
    private final Map<String, String> sessionUsers = new ConcurrentHashMap<>();

    @EventListener
    public void onWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = sha.getSessionId();
        String user = sha.getUser() != null ? sha.getUser().getName() : "anonymous";
        sessionUsers.put(sessionId, user);
        int count = activeSessions.incrementAndGet();
        log.info("[WS] Session CONNECTED: sessionId={}, user={}, activeSessions={}", sessionId, user, count);
    }

    @EventListener
    public void onWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = sha.getSessionId();
        String user = sessionUsers.remove(sessionId);
        int count = activeSessions.decrementAndGet();
        log.info("[WS] Session DISCONNECTED: sessionId={}, user={}, activeSessions={}", sessionId, user, count);
    }

    @EventListener
    public void onSubscribeEvent(SessionSubscribeEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = sha.getSessionId();
        String user = sha.getUser() != null ? sha.getUser().getName() : "anonymous";
        String destination = sha.getDestination();
        log.debug("[WS] SUBSCRIBE: sessionId={}, user={}, destination={}", sessionId, user, destination);
    }

    @EventListener
    public void onUnsubscribeEvent(SessionUnsubscribeEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = sha.getSessionId();
        String subscriptionId = sha.getSubscriptionId();
        log.debug("[WS] UNSUBSCRIBE: sessionId={}, subscriptionId={}", sessionId, subscriptionId);
    }

    public int getActiveSessionCount() {
        return activeSessions.get();
    }
}
