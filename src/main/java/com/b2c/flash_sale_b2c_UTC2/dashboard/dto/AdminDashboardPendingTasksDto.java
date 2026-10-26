package com.b2c.flash_sale_b2c_UTC2.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDashboardPendingTasksDto {
    private Long pendingStores;
    private Long pendingFlashSaleItems;
    private Long pendingWithdrawals;
    private Long reportedReviews;
}
