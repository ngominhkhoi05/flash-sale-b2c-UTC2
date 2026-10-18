package com.b2c.flash_sale_b2c_UTC2.flashsale.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Entity đại diện cho bảng flash_sale_slots (Khung giờ Flash Sale do Admin mở).
 */
@Entity
@Table(name = "flash_sale_slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashSaleSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "reservation_ttl_seconds")
    @Builder.Default
    private Integer reservationTtlSeconds = 300;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "UPCOMING";

    @Column(name = "is_stock_rolled_back")
    @Builder.Default
    private Boolean isStockRolledBack = false;

    @Column(name = "created_at")
    private Instant createdAt;
}
