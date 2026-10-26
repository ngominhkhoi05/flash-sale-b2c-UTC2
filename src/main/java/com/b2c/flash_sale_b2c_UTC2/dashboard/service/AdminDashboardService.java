package com.b2c.flash_sale_b2c_UTC2.dashboard.service;

import com.b2c.flash_sale_b2c_UTC2.dashboard.dto.AdminDashboardSummaryResponse;

import java.time.LocalDate;

public interface AdminDashboardService {

    AdminDashboardSummaryResponse getDashboardSummary(
            String periodType,
            Integer year,
            Integer month,
            LocalDate date,
            LocalDate startDate,
            LocalDate endDate
    );
}
