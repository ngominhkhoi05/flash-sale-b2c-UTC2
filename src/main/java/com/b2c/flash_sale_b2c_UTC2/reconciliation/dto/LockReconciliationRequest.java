package com.b2c.flash_sale_b2c_UTC2.reconciliation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LockReconciliationRequest {
    @NotBlank(message = "Kỳ đối soát không được để trống (ví dụ: 2026-10 hoặc 10/2026)")
    private String period;
    private String notes;
}
