package com.b2c.flash_sale_b2c_UTC2.product.enums;

/**
 * Hard-coded service-flag facet for storefront search.
 *
 * <p>Maps to existing product flags where possible:</p>
 * <ul>
 *   <li>{@link #FLASH_SALE} - filter via {@code flashSaleOnly=true}</li>
 *   <li>{@link #VOUCHER} - filter via {@code hasDiscount=true}</li>
 *   <li>{@link #FREESHIP} - no DB column yet (count=0 for facet)</li>
 *   <li>{@link #OFFICIAL} - no DB column yet (count=0 for facet)</li>
 * </ul>
 *
 * <p>NOTE: this enum drives the sidebar facet only in the initial release.
 * The corresponding data-layer support is incremental per service flag.</p>
 */
public enum SearchService {
    FLASH_SALE("flash-sale", "Đang Flash Sale"),
    VOUCHER("voucher", "Có Voucher"),
    FREESHIP("freeship", "Freeship"),
    OFFICIAL("official", "Mall chính hãng");

    private final String key;
    private final String label;

    SearchService(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public static SearchService fromKey(String key) {
        if (key == null) {
            return null;
        }
        for (SearchService s : values()) {
            if (s.key.equalsIgnoreCase(key)) {
                return s;
            }
        }
        return null;
    }
}
