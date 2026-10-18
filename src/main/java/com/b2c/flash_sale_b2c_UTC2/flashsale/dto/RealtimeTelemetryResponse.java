package com.b2c.flash_sale_b2c_UTC2.flashsale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeTelemetryResponse {
    private Long slotId;
    private String status;
    private Boolean isPaused;
    private Long currentRps;
    private Long totalRequests;
    private Long activeLocks;
    private Long successfulOrders;
    private BigDecimal totalRevenue;
    private Long failedRequests;
    private Long expiredReleases;
    private Integer oversoldIncidents;
    private Double redisLatencyMs;
    private String systemHealth;
    private Instant lastUpdated;
}
