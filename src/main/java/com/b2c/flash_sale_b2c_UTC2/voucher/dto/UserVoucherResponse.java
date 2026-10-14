package com.b2c.flash_sale_b2c_UTC2.voucher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO trả về cho màn hình Kho Voucher của người dùng (GET /api/v1/vouchers/mine và POST /api/v1/vouchers/mine).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVoucherResponse {
    private Long id;
    private String code;
    private String label;
    private String scope;
    private Long storeId;
    private String storeName;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal maxDiscount;
    private BigDecimal minOrderAmount;
    private Integer userUsageLimit;
    private Integer usedQuantity;
    private Integer totalQuantity;
    private Instant startTime;
    private Instant endTime;
    private String status;
}
