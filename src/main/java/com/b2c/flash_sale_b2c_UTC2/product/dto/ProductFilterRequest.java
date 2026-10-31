package com.b2c.flash_sale_b2c_UTC2.product.dto;

import com.b2c.flash_sale_b2c_UTC2.product.enums.ProductSortOption;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFilterRequest {

    private Integer categoryId;
    /** Danh sách category slug hoặc id để lọc nhiều danh mục cùng lúc. */
    private java.util.List<String> categories;
    private String keyword;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    /** Minimum rating average filter (ratingAvg >= value). */
    private Integer rating;
    /** If true, only products with at least one active flash sale item. */
    private Boolean flashSaleOnly;
    /** If true, only products where minPrice < maxPrice (on sale/discounted). */
    private Boolean hasDiscount;
    /** Minimum sold count filter (soldCount >= value). */
    private Integer minSold;
    /** Sort option (default: CREATED_AT_DESC). */
    private ProductSortOption sortOption;
}
