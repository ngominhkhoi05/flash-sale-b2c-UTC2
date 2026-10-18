package com.b2c.flash_sale_b2c_UTC2.voucher.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateVoucherStatusRequest {

    @NotBlank(message = "Trạng thái voucher không được để trống")
    private String status;
}
