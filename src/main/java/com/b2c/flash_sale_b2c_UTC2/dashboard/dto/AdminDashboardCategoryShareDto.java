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
public class AdminDashboardCategoryShareDto {
    private Long categoryId;
    private String name;
    private BigDecimal revenue;
    private Double sharePercentage;
}
