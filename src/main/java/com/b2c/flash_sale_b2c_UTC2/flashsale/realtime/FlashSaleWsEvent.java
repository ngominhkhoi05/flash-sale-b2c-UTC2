package com.b2c.flash_sale_b2c_UTC2.flashsale.realtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Payload gửi qua WebSocket STOMP cho Flash Sale realtime events.
 * Format chuẩn hóa theo {@code ApiResponse<T>} đã có trong common/.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlashSaleWsEvent {

    // ── Event type ──────────────────────────────────────────────
    private EventType eventType;

    // ── Context ─────────────────────────────────────────────────
    private Long slotId;
    private Long flashSaleItemId;
    private Long orderId;
    private Long userId;

    // ── Payload ─────────────────────────────────────────────────
    /** Số tồn kho khả dụng (cho STOCK_* events) */
    private Integer availableStock;

    /** Tổng tồn kho phân bổ (cho STOCK_* events) */
    private Integer allocatedStock;

    /** Giá flash sale (cho ORDER_RESERVED) */
    private Long totalAmount;

    /** Số lượng mua thành công (cho ORDER_RESERVED) */
    private Integer quantity;

    /** Mã đơn hàng (cho ORDER_*) */
    private String orderCode;

    /** Thời gian hết hạn giữ chỗ (cho ORDER_RESERVED) */
    private Instant expiresAt;

    /** Trạng thái slot mới (cho SLOT_*) */
    private String slotStatus;

    /** Số lượng stock đã restore (cho STOCK_RESTORED) */
    private Integer restoredQuantity;

    // ── Meta ─────────────────────────────────────────────────────
    private Instant occurredAt;

    // ── Event type enum ──────────────────────────────────────────

    /**
     * Loại event realtime broadcast qua WebSocket.
     */
    public enum EventType {
        /** Trừ tồn kho nguyên tử trên Redis thành công */
        STOCK_DECREMENTED,

        /** Hoàn stock do timeout/hủy đơn */
        STOCK_RESTORED,

        /** Hoàn stock hàng ế khi slot kết thúc */
        STOCK_RETURNED_UNSOLD,

        /** Slot chuyển từ UPCOMING → ACTIVE */
        SLOT_ACTIVATED,

        /** Slot chuyển sang ENDED */
        SLOT_CLOSED,

        /** Đơn giữ chỗ tạo thành công (gửi riêng user) */
        ORDER_RESERVED,

        /** Đơn hết hạn giữ chỗ bị hủy (gửi riêng user) */
        ORDER_CANCELLED_TIMEOUT
    }
}