package com.b2c.flash_sale_b2c_UTC2.voucher.dto;

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
public class SaveVoucherRequest {

    @NotBlank(message = "Mã giảm giá không được để trống")
    @Size(max = 30, message = "Mã giảm giá không quá 30 ký tự")
    private String code;
}
