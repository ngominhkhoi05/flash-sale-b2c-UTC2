package com.b2c.flash_sale_b2c_UTC2.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetailResponse {

    private Long id;
    private Long storeId;
    private String storeName;
    private Integer categoryId;
    private String categoryName;
    private String name;
    private String imageUrl;
    private String description;
    private List<TierVariationConfigDto> tierVariationConfigs;
    private String status;
    private Instant createdAt;
    private List<ProductVariantResponse> variants;

    // ─── Subplan 2: new fields for product detail gap ─────────────────────────────
    /** Trung bình rating từ reviews VISIBLE (0.0 nếu không có review). */
    private Double ratingAvg;
    /** Tổng số review VISIBLE. */
    private Integer ratingCount;
    /** Tổng số đã bán (products.total_sold V7). */
    private Integer soldCount;
    /** Giá thấp nhất trong các variant ACTIVE. */
    private BigDecimal minPrice;
    /** Giá cao nhất trong các variant ACTIVE. */
    private BigDecimal maxPrice;
    /**
     * % giảm giá Flash Sale (làm tròn).
     * null nếu sản phẩm không có Flash SaleItem ACTIVE.
     * Tính: round((original - flashSale) / original * 100).
     */
    private Integer discountPercent;
    /** Danh sách ảnh sản phẩm (từ bảng images, sort theo displayOrder). */
    private List<ProductImageDto> images;
}
