package com.b2c.flash_sale_b2c_UTC2.wallet.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveWithdrawalRequest {

    @Size(max = 255, message = "Ghi chú không vượt quá 255 ký tự")
    private String adminNote;
}
