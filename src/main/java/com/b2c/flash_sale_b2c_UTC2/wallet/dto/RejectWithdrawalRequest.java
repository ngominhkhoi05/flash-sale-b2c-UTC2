package com.b2c.flash_sale_b2c_UTC2.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectWithdrawalRequest {

    @NotBlank(message = "Lý do từ chối không được để trống")
    @Size(min = 5, max = 255, message = "Lý do từ chối từ 5 đến 255 ký tự")
    private String reason;
}
