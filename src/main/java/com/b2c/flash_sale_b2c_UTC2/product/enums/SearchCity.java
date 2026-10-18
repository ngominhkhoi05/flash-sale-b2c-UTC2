package com.b2c.flash_sale_b2c_UTC2.product.enums;

/**
 * Hard-coded city facet for storefront search.
 *
 * <p>NOTE: the underlying {@code Store} entity does not yet have a city column
 * (see plan {@code docs/superpowers/plans/2026-10-18-search-endpoint-be-wire-fe.md}).
 * For the initial release, this enum drives the "Nơi bán" sidebar facet only —
 * it does not currently filter the database query. Once a {@code city} column
 * is added to {@code stores} (out of scope for this plan), the filter will be
 * wired through.</p>
 */
public enum SearchCity {
    HCM("HCM", "TP. Hồ Chí Minh"),
    HN("HN", "Hà Nội"),
    DN("DN", "Đà Nẵng"),
    BD("BD", "Bình Dương");

    private final String key;
    private final String label;

    SearchCity(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public static SearchCity fromKey(String key) {
        if (key == null) {
            return null;
        }
        for (SearchCity c : values()) {
            if (c.key.equalsIgnoreCase(key)) {
                return c;
            }
        }
        return null;
    }
}
