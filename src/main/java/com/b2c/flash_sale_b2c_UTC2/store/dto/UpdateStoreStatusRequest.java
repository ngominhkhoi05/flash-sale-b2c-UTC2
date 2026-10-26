package com.b2c.flash_sale_b2c_UTC2.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStoreStatusRequest {

    @NotBlank(message = "Trạng thái gian hàng không được để trống")
    @Pattern(regexp = "APPROVED|BANNED|SUSPENDED|PENDING|REJECTED", message = "Trạng thái chỉ có thể là APPROVED, BANNED, SUSPENDED, PENDING hoặc REJECTED")
    private String status;

    private String reason;

    public UpdateStoreStatusRequest(String status) {
        this.status = status;
    }
}
