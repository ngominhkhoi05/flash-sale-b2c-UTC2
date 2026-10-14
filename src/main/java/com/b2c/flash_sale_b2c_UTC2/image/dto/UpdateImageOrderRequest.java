package com.b2c.flash_sale_b2c_UTC2.image.dto;

import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO dùng cho PATCH /api/v1/images/{id}/order — cập nhật thứ tự hiển thị.
 * Chỉ áp dụng cho REVIEW (gallery 1-N).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateImageOrderRequest {

    @NotNull(message = "Thứ tự hiển thị không được để trống")
    @Min(value = 0, message = "Thứ tự hiển thị phải >= 0")
    private Integer displayOrder;
}