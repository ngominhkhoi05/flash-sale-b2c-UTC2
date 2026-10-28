package com.b2c.flash_sale_b2c_UTC2.flashsale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleSlotResponse {
    private Long id;
    private String title;
    private Instant startTime;
    private Instant endTime;
    private Integer reservationTtlSeconds;
    private String status;
    private Instant createdAt;

    // Các trường thống kê SKU thực tế từ bảng flash_sale_items
    private Long approvedSkusCount;
    private Long pendingSkusCount;
    private Long totalSkusCount;
    private Integer allocatedQuantity;
    private Integer soldQuantity;
}
