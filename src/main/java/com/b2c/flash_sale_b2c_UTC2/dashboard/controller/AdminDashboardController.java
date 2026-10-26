package com.b2c.flash_sale_b2c_UTC2.dashboard.controller;

import com.b2c.flash_sale_b2c_UTC2.common.api.ApiResponse;
import com.b2c.flash_sale_b2c_UTC2.dashboard.dto.AdminDashboardSummaryResponse;
import com.b2c.flash_sale_b2c_UTC2.dashboard.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Admin Dashboard", description = "APIs báo cáo & thống kê tổng quan sàn cho Quản trị viên")
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @Operation(summary = "Lấy báo cáo tổng quan sàn theo ngày/tháng/năm",
               description = "Thống kê GMV, số đơn, người dùng mới, gian hàng mới, chuỗi thời gian, top gian hàng và cơ cấu ngành hàng")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminDashboardSummaryResponse>> getSummary(
            @RequestParam(required = false, defaultValue = "MONTH") String periodType,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        AdminDashboardSummaryResponse summary = adminDashboardService.getDashboardSummary(
                periodType, year, month, date, startDate, endDate
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy báo cáo tổng quan hệ thống thành công", summary));
    }
}
