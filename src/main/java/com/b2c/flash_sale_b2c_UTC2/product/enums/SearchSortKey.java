package com.b2c.flash_sale_b2c_UTC2.product.enums;

import java.util.Arrays;

/**
 * Sort key for {@code GET /api/v1/products/search}.
 *
 * <p>Mirrors the FE storefront search UI sort options. Maps to the underlying
 * {@link ProductSortOption} used by the public product list query.</p>
 */
public enum SearchSortKey {
    RELEVANCE("relevance"),
    NEWEST("newest"),
    BEST_SELLING("best_selling"),
    PRICE_ASC("price_asc"),
    PRICE_DESC("price_desc");

    private final String value;

    SearchSortKey(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse a raw string into a {@link SearchSortKey}. Returns {@link #RELEVANCE}
     * for null/blank or unknown values (lenient parsing for resilience).
     */
    public static SearchSortKey fromValue(String value) {
        if (value == null || value.isBlank()) {
            return RELEVANCE;
        }
        return Arrays.stream(values())
                .filter(k -> k.value.equalsIgnoreCase(value))
                .findFirst()
                .orElse(RELEVANCE);
    }

    /**
     * Map to the internal {@link ProductSortOption} used by the data layer.
     * RELEVANCE and NEWEST both fall back to CREATED_AT_DESC.
     */
    public ProductSortOption toProductSortOption() {
        return switch (this) {
            case PRICE_ASC -> ProductSortOption.PRICE_ASC;
            case PRICE_DESC -> ProductSortOption.PRICE_DESC;
            case BEST_SELLING -> ProductSortOption.SOLD_DESC;
            case RELEVANCE, NEWEST -> ProductSortOption.CREATED_AT_DESC;
        };
    }
}
