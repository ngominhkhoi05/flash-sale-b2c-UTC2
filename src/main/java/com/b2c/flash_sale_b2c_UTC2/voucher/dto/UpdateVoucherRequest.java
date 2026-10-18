package com.b2c.flash_sale_b2c_UTC2.voucher.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateVoucherRequest {

    @Size(max = 100, message = "Tên chương trình voucher tối đa 100 ký tự")
    private String name;

    private String discountType;

    @Positive(message = "Giá trị giảm giá phải lớn hơn 0")
    private BigDecimal discountValue;

    @PositiveOrZero(message = "Giá trị đơn hàng tối thiểu không được âm")
    private BigDecimal minOrderAmount;

    @Positive(message = "Giá trị giảm tối đa phải lớn hơn 0")
    private BigDecimal maxDiscountAmount;

    @Positive(message = "Tổng số lượng voucher phải lớn hơn 0")
    private Integer totalQuantity;

    @Positive(message = "Giới hạn sử dụng mỗi user phải lớn hơn 0")
    private Integer userUsageLimit;

    private Instant startTime;

    private Instant endTime;

    private String status;
}
