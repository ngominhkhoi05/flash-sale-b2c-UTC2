package com.b2c.flash_sale_b2c_UTC2.flashsale.realtime;

import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service broadcast Flash Sale realtime events qua STOMP /user & /topic destinations.
 *
 * <p>
 * Dùng {@link SimpMessagingTemplate} gửi message từ server → client.
 * Cấu hình destination:
 * </p>
 *
 * <ul>
 *   <li>{@code /topic/flash-sale/slot/{slotId}/stock-update} — public: stock thay đổi,
 *       ai subscribe đều nhận.</li>
 *   <li>{@code /topic/flash-sale/slot/{slotId}/status} — public: slot status thay đổi.</li>
 *   <li>{@code /topic/flash-sale/item/{itemId}/stock} — public: stock cụ thể 1 SKU.</li>
 *   <li>{@code /user/queue/orders/{orderCode}/updates} — private: chỉ user sở hữu đơn nhận.</li>
 *   <li>{@code /user/queue/flash-sale/reservation-result} — private: kết quả reservation.</li>
 * </ul>
 *
 * <p>
 * <b>Lưu ý:</b> Đây chỉ là broadcast layer. Business logic (Lua atomicity,
 * DB transaction) không thay đổi. WS chỉ push thông báo <b>sau khi</b>
 * các thay đổi đã xảy ra.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleWsBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    // ── Topic destinations ───────────────────────────────────────

    /** /topic/flash-sale/slot/{slotId}/stock-update */
    public void broadcastStockUpdate(Long slotId, Long itemId, int availableStock) {
        FlashSaleWsEvent event = FlashSaleWsEvent.builder()
                .eventType(FlashSaleWsEvent.EventType.STOCK_DECREMENTED)
                .slotId(slotId)
                .flashSaleItemId(itemId)
                .availableStock(availableStock)
                .occurredAt(Instant.now())
                .build();

        String dest = WsDestinations.slotStockUpdate(slotId);
        sendToTopic(dest, event);
        log.debug("[WS] Broadcast STOCK_DECREMENTED to {}: itemId={}, stock={}",
                dest, itemId, availableStock);
    }

    /** /topic/flash-sale/slot/{slotId}/stock-update (stock restored / rollback) */
    public void broadcastStockRestored(Long slotId, Long itemId, int availableStock, int restoredQuantity) {
        FlashSaleWsEvent event = FlashSaleWsEvent.builder()
                .eventType(FlashSaleWsEvent.EventType.STOCK_RESTORED)
                .slotId(slotId)
                .flashSaleItemId(itemId)
                .availableStock(availableStock)
                .restoredQuantity(restoredQuantity)
                .occurredAt(Instant.now())
                .build();

        String dest = WsDestinations.slotStockUpdate(slotId);
        sendToTopic(dest, event);
        log.debug("[WS] Broadcast STOCK_RESTORED to {}: itemId={}, stock={}, restored={}",
                dest, itemId, availableStock, restoredQuantity);
    }

    /** /topic/flash-sale/slot/{slotId}/stock-update (unsold returned after slot end) */
    public void broadcastUnsoldStockReturned(Long slotId, Long itemId, int availableStock) {
        FlashSaleWsEvent event = FlashSaleWsEvent.builder()
                .eventType(FlashSaleWsEvent.EventType.STOCK_RETURNED_UNSOLD)
                .slotId(slotId)
                .flashSaleItemId(itemId)
                .availableStock(availableStock)
                .occurredAt(Instant.now())
                .build();

        String dest = WsDestinations.slotStockUpdate(slotId);
        sendToTopic(dest, event);
        log.debug("[WS] Broadcast STOCK_RETURNED_UNSOLD to {}: itemId={}, stock={}",
                dest, itemId, availableStock);
    }

    /** /topic/flash-sale/slot/{slotId}/status (UPCOMING → ACTIVE hoặc ACTIVE → ENDED) */
    public void broadcastSlotStatus(Long slotId, String newStatus) {
        FlashSaleWsEvent event = FlashSaleWsEvent.builder()
                .eventType(
                        "ACTIVE".equals(newStatus)
                                ? FlashSaleWsEvent.EventType.SLOT_ACTIVATED
                                : FlashSaleWsEvent.EventType.SLOT_CLOSED
                )
                .slotId(slotId)
                .slotStatus(newStatus)
                .occurredAt(Instant.now())
                .build();

        String dest = WsDestinations.slotStatus(slotId);
        sendToTopic(dest, event);
        log.debug("[WS] Broadcast slot status {} to {}: slotId={}", newStatus, dest, slotId);
    }

    // ── Private user destinations ────────────────────────────────

    /**
     * Gửi kết quả reservation thành công riêng tới user.
     * /user/{username}/queue/flash-sale/reservation-result
     */
    public void sendReservationResultToUser(String username, FlashSaleWsEvent event) {
        String dest = WsDestinations.reservationResult();
        sendToUser(username, dest, event);
        log.debug("[WS] Send ORDER_RESERVED to user {}: orderCode={}", username, event.getOrderCode());
    }

    /**
     * Gửi thông báo đơn hết hạn riêng tới user.
     * /user/{username}/queue/orders/{orderCode}/updates
     */
    public void sendOrderCancelledToUser(String username, FlashSaleWsEvent event) {
        String dest = WsDestinations.orderUpdates(event.getOrderCode());
        sendToUser(username, dest, event);
        log.debug("[WS] Send ORDER_CANCELLED_TIMEOUT to user {}: orderCode={}",
                username, event.getOrderCode());
    }

    // ── Helpers ────────────────────────────────────────────────

    private void sendToTopic(String destination, FlashSaleWsEvent payload) {
        ApiResponse<FlashSaleWsEvent> response = ApiResponse.success(
                "Flash Sale update",
                payload
        );
        messagingTemplate.convertAndSend(destination, response);
    }

    /**
     * Gửi tới user cụ thể qua Spring STOMP /user/ prefix.
     * Spring tự động route tới đúng WebSocket session của user đó.
     * Nếu user không online, message bị discard (không queue).
     */
    private void sendToUser(String username, String destination, FlashSaleWsEvent payload) {
        ApiResponse<FlashSaleWsEvent> response = ApiResponse.success(
                "Order update",
                payload
        );
        messagingTemplate.convertAndSendToUser(username, destination, response);
    }
}
