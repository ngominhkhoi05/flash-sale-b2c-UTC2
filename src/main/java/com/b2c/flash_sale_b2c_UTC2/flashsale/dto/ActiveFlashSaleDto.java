package com.b2c.flash_sale_b2c_UTC2.flashsale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveFlashSaleDto {
    private Long slotId;
    private Instant slotStartTime;
    private Instant slotEndTime;
    private String slotStatus;
    private Long itemId;
    private Long variantId;
    private BigDecimal originalPrice;
    private BigDecimal flashSalePrice;
    private Integer stockAvailable;
}