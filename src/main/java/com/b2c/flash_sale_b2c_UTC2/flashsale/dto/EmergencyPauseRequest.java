package com.b2c.flash_sale_b2c_UTC2.flashsale.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyPauseRequest {

    @NotBlank(message = "Lý do dừng khẩn cấp không được để trống")
    private String reason;

    @Builder.Default
    private Boolean rollbackStock = true;
}
