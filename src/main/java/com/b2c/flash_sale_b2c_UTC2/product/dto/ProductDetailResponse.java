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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Builder.Default
    private String description = "";

    @Builder.Default
    private List<TierVariationConfigDto> tierVariationConfigs = new ArrayList<>();

    private String status;
    private Instant createdAt;

    @Builder.Default
    private List<ProductVariantResponse> variants = new ArrayList<>();

    // Subplan 2: new fields for product detail gap
    /** Trung binh rating tu reviews VISIBLE (0.0 neu khong co review). */
    @Builder.Default
    private Double ratingAvg = 0.0;

    /** Tong so review VISIBLE. */
    @Builder.Default
    private Integer ratingCount = 0;

    /** Tong so da ban (products.total_sold V7). */
    @Builder.Default
    private Integer soldCount = 0;

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
    @Builder.Default
    private List<ProductImageDto> images = new ArrayList<>();

    // Part A Subplan 4: active flash sales list for product detail
    @Builder.Default
    private List<ActiveFlashSaleDto> flashSales = new ArrayList<>();

    // Gap Report 2026-10-13 additions for Frontend UI integration
    @Builder.Default
    private String slug = "";

    @Builder.Default
    private Object descriptionRich = new ArrayList<>();

    @Builder.Default
    private Object specs = new HashMap<>();

    @Builder.Default
    private Object features = new ArrayList<>();

    @Builder.Default
    private Object commitments = new ArrayList<>();

    @Builder.Default
    private Object vouchers = new ArrayList<>();

    @Builder.Default
    private Object storeProfile = new HashMap<>();

    @Builder.Default
    private Object shipping = new HashMap<>();

    @Builder.Default
    private Object sizeChart = new ArrayList<>();

    @Builder.Default
    private RatingDistributionDto ratingDistribution = RatingDistributionDto.builder().build();

    @Builder.Default
    private Integer questionCount = 0;

    @Builder.Default
    private List<ProductSummaryResponse> relatedSameStore = new ArrayList<>();

    @Builder.Default
    private List<ProductSummaryResponse> relatedBestSellers = new ArrayList<>();
}

