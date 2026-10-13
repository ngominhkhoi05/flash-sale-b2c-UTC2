package com.b2c.flash_sale_b2c_UTC2.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Aggregated metrics response for seller dashboard sidebar.
 * GET /api/v1/seller/sidebar/summary
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SidebarSummaryResponse {

    private Long totalProducts;

    private Long activeFlashSales;

    private Long pendingOrders;

    private Long unpaidOrders;

    private Long todayOrders;

    private BigDecimal todayRevenue;

    private BigDecimal monthRevenue;

    private Double ratingAvg;

    private Long ratingCount;

    private Long lowStockProducts;

    private String storeStatus;

    private String tier;
}