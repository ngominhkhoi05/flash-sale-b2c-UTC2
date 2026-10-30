package com.b2c.flash_sale_b2c_UTC2.flashsale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicFlashSaleItemDetailResponse {
    // Flash Sale Item & Slot Info
    private Long id;
    private Long slotId;
    private String slotTitle;
    private Instant slotStartTime;
    private Instant slotEndTime;
    private String slotStatus;
    private Integer reservationTtlSeconds;

    // Pricing & Inventory
    private BigDecimal originalPrice;
    private BigDecimal flashSalePrice;
    private Integer discountPercent;
    private Integer allocatedStock;
    private Integer availableStock;
    private Integer userPurchaseLimit;
    private String status;

    // Product SPU Info
    private Long productId;
    private String productName;
    private String productDescription;
    private String productSlug;
    private Integer totalSold;
    private Double ratingAvg;
    private Long ratingCount;

    // Category
    private Integer categoryId;
    private String categoryName;
    private String categorySlug;

    // Store
    private Long storeId;
    private String storeName;
    private String storeTier;
    private String storeLogoUrl;

    // Target Variant (SKU) Info - Cố định biến thể Flash Sale
    private Long variantId;
    private String sku;
    private String variantName;
    private Map<String, String> attributes;

    // Images
    private String primaryImageUrl;
    private List<String> imageUrls;
}
