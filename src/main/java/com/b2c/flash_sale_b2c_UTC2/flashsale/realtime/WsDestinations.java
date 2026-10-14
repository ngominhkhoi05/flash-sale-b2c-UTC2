package com.b2c.flash_sale_b2c_UTC2.flashsale.realtime;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Centralized WebSocket STOMP destination patterns cho Flash Sale realtime channel.
 *
 * <p>
 * Mọi topic/queue destination phải được build qua class này để:
 * <ul>
 *   <li>Tránh sai URL pattern rải rác ở nhiều nơi</li>
 *   <li>Dễ refactor nếu đổi structure (vd thêm version prefix {@code /v1})</li>
 *   <li>Client + server thống nhất qua single source of truth</li>
 * </ul>
 * </p>
 *
 * <h3>Convention:</h3>
 * <ul>
 *   <li>{@code /topic/...} — broadcast public (ai subscribe cũng nhận)</li>
 *   <li>{@code /queue/...} — queue per-user (Spring tự prefix {@code /user/{username}})</li>
 * </ul>
 *
 * <h3>Subscribe URL (client side):</h3>
 * <pre>
 *   // Public topic — ai cũng subscribe được
 *   stompClient.subscribe('/topic/flash-sale/item/42/stock', handler);
 *   stompClient.subscribe('/topic/flash-sale/slot/7/status', handler);
 *
 *   // Private queue — chỉ user tương ứng nhận
 *   stompClient.subscribe('/user/queue/flash-sale/reservation-result', handler);
 *   stompClient.subscribe('/user/queue/flash-sale/orders/ORD-2026.../updates', handler);
 * </pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WsDestinations {

    // ── Prefix constants ───────────────────────────────────────
    private static final String TOPIC_PREFIX = "/topic/flash-sale";
    private static final String QUEUE_PREFIX = "/queue/flash-sale";

    // ── Public topic destinations (broadcast) ─────────────────

    /**
     * Stock update cho 1 SKU cụ thể.
     * <p>VD: {@code /topic/flash-sale/item/42/stock}</p>
     */
    public static String itemStock(Long itemId) {
        return TOPIC_PREFIX + "/item/" + itemId + "/stock";
    }

    /**
     * Stock update cho cả slot (mọi item trong slot).
     * <p>VD: {@code /topic/flash-sale/slot/7/stock-update}</p>
     */
    public static String slotStockUpdate(Long slotId) {
        return TOPIC_PREFIX + "/slot/" + slotId + "/stock-update";
    }

    /**
     * Slot status thay đổi (UPCOMING → ACTIVE → ENDED).
     * <p>VD: {@code /topic/flash-sale/slot/7/status}</p>
     */
    public static String slotStatus(Long slotId) {
        return TOPIC_PREFIX + "/slot/" + slotId + "/status";
    }

    // ── Private queue destinations (per-user) ─────────────────

    /**
     * Kết quả reservation riêng cho user.
     * Spring tự đổi thành {@code /user/{username}/queue/flash-sale/reservation-result}.
     */
    public static String reservationResult() {
        return QUEUE_PREFIX + "/reservation-result";
    }

    /**
     * Update riêng cho 1 đơn hàng của user (timeout, payment, v.v.).
     * Spring tự đổi thành {@code /user/{username}/queue/flash-sale/orders/{orderCode}/updates}.
     *
     * @param orderCode mã đơn hàng
     */
    public static String orderUpdates(String orderCode) {
        return QUEUE_PREFIX + "/orders/" + orderCode + "/updates";
    }
}