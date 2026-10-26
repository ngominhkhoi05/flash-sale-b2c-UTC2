package com.b2c.flash_sale_b2c_UTC2.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDashboardChartPointDto {
    private String label;
    private BigDecimal revenue;
    private Long orders;
}
