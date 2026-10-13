package com.b2c.flash_sale_b2c_UTC2.product.dto;

import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.ActiveFlashSaleDto;
import com.b2c.flash_sale_b2c_UTC2.review.dto.RatingDistributionDto;
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

    // Subplan 2: new fields for product detail gap
    /** Trung binh rating tu reviews VISIBLE (0.0 neu khong co review). */
    private Double ratingAvg;
    /** Tong so review VISIBLE. */
    private Integer ratingCount;
    /** Tong so da ban (products.total_sold V7). */
    private Integer soldCount;
    /** Gia thap nhat trong cac variant ACTIVE. */
    private BigDecimal minPrice;
    /** Gia cao nhat trong cac variant ACTIVE. */
    private BigDecimal maxPrice;
    /**
     * % giam gia Flash Sale (lam tron).
     * null neu san pham khong co Flash SaleItem ACTIVE.
     * Tinh: round((original - flashSale) / original * 100).
     */
    private Integer discountPercent;
    /** Danh sach anh san pham (tu bang images, sort theo displayOrder). */
    private List<ProductImageDto> images;

    // Part A Subplan 4: active flash sales list for product detail
    private List<ActiveFlashSaleDto> flashSales;

    // Gap Report 2026-10-13 additions for Frontend UI integration
    private String slug;
    private Object descriptionRich;
    private Object specs;
    private Object features;
    private Object commitments;
    private Object vouchers;
    private Object storeProfile;
    private Object shipping;
    private Object sizeChart;
    private RatingDistributionDto ratingDistribution;
    private Integer questionCount;
    private List<ProductSummaryResponse> relatedSameStore;
    private List<ProductSummaryResponse> relatedBestSellers;
}
