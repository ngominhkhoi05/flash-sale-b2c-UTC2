package com.b2c.flash_sale_b2c_UTC2.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDashboardSummaryResponse {
    private String periodType;
    private String periodLabel;
    private Instant startDate;
    private Instant endDate;

    // 4 KPI chính
    private BigDecimal totalRevenue;
    private Double revenueGrowthPercent;

    private Long totalOrders;
    private Double ordersGrowthPercent;

    private Long newUsers;
    private Double usersGrowthPercent;

    private Long newStores;
    private Double storesGrowthPercent;

    // Biểu đồ theo thời gian
    @Builder.Default
    private List<AdminDashboardChartPointDto> chartPoints = new ArrayList<>();

    // Top gian hàng
    @Builder.Default
    private List<AdminDashboardTopStoreDto> topStores = new ArrayList<>();

    // Cơ cấu ngành hàng
    @Builder.Default
    private List<AdminDashboardCategoryShareDto> categoryShares = new ArrayList<>();

    // Nhiệm vụ chờ xử lý
    private AdminDashboardPendingTasksDto pendingTasks;
}
