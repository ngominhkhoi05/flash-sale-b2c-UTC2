package com.b2c.flash_sale_b2c_UTC2.product.enums;

public enum ProductSortOption {
    CREATED_AT_DESC("createdAt_desc"),
    CREATED_AT_ASC("createdAt_asc"),
    PRICE_ASC("price_asc"),
    PRICE_DESC("price_desc"),
    SOLD_DESC("sold_desc"),
    RATING_DESC("rating_desc");

    private final String value;

    ProductSortOption(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ProductSortOption fromValue(String value) {
        if (value == null || value.isBlank()) {
            return CREATED_AT_DESC;
        }
        for (ProductSortOption opt : values()) {
            if (opt.value.equalsIgnoreCase(value)) {
                return opt;
            }
        }
        return CREATED_AT_DESC;
    }
}