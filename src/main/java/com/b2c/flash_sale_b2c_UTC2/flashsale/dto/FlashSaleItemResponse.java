package com.b2c.flash_sale_b2c_UTC2.flashsale.dto;

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
public class FlashSaleItemResponse {
    private Long id;
    private Long slotId;
    private Long variantId;
    private BigDecimal flashSalePrice;
    private Integer allocatedStock;
    private Integer availableStock;
    private Integer userPurchaseLimit;
    private BigDecimal commissionRateOverride;
    private String status;
    private Instant createdAt;

    // Enriched fields for Seller & Admin UI
    private String slotTitle;
    private Instant slotStartTime;
    private Instant slotEndTime;
    private String slotStatus;
    private Long storeId;
    private String storeName;
    private Long productId;
    private String productName;
    private String skuCode;
    private String variantName;
    private String imageUrl;
    private BigDecimal originalPrice;
}
