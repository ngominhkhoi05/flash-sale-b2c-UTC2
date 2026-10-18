package com.b2c.flash_sale_b2c_UTC2.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One facet bucket item: a label, a key, and a count for sidebar display.
 *
 * <p>Used inside {@link SearchFacetsDto} for subcategories, cities, ratings,
 * and services. The {@code key} is the canonical string used by the FE
 * to drive checkboxes; {@code label} is the user-visible display text.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacetItemDto {
    private String key;
    private String label;
    private long count;
}
