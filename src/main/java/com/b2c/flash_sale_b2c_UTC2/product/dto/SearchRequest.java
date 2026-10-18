package com.b2c.flash_sale_b2c_UTC2.product.dto;

import com.b2c.flash_sale_b2c_UTC2.product.enums.SearchService;
import com.b2c.flash_sale_b2c_UTC2.product.enums.SearchSortKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Input to {@code ProductServiceImpl.searchProducts}. Encapsulates the
 * storefront search UI parameters in a single value object so the service
 * signature stays clean and the test fixtures are easy to construct.
 *
 * <p>The controller is responsible for translating HTTP query params into
 * this object; the service does no HTTP-aware work.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {
    /** Free-text search across product names. */
    private String q;

    /** Subcategory keys (e.g. true-wireless, over-ear). Mapped to {@code categoryId} by the service. */
    private List<String> subcategories;

    /** City facet values (HCM | HN | DN | BD). Currently drives the facet only. */
    private List<String> cities;

    /** Service flags (flash-sale | voucher | freeship | official). Mapped to internal filters where supported. */
    private List<SearchService> services;

    private BigDecimal priceMin;
    private BigDecimal priceMax;

    /** Minimum rating (3 | 4 | 5). */
    private Integer rating;

    private SearchSortKey sort;
}
