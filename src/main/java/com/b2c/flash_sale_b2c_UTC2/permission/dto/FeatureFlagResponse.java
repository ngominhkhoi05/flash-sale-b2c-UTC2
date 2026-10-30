package com.b2c.flash_sale_b2c_UTC2.permission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlagResponse {
    private String code;
    private String name;
    private String description;
    private String module;
    private int groupCount;
    private boolean isEnabled;
    private String updatedAt;
    private String updatedBy;
    private String maintenanceReason;
}
