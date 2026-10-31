package com.b2c.flash_sale_b2c_UTC2.flashsale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicFlashSaleSlotResponse {
    private Long id;
    private String title;
    private Instant startTime;
    private Instant endTime;
    private Integer reservationTtlSeconds;
    private String status;
    private Long participatingStoresCount;
    private Integer totalItemsCount;
    private List<PublicFlashSaleItemResponse> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublicFlashSaleItemResponse {
        private Long id;
        private Long slotId;
        private Long productId;
        private Long variantId;
        private Integer categoryId;
        private String categoryName;
        private String categorySlug;
        private String sku;
        private String variantName;
        private java.util.Map<String, String> attributes;
        private String productName;
        private String imageUrl;
        private BigDecimal originalPrice;
        private BigDecimal flashSalePrice;
        private Integer allocatedStock;
        private Integer availableStock;
        private Integer userPurchaseLimit;
        private String status;
    }
}
