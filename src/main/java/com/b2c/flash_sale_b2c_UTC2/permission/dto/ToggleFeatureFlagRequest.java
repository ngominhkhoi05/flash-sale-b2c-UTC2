package com.b2c.flash_sale_b2c_UTC2.permission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToggleFeatureFlagRequest {
    private boolean isEnabled;
    private String maintenanceReason;
}
