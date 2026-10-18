package com.b2c.flash_sale_b2c_UTC2.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

/**
 * Facets returned alongside search results so the FE sidebar can render
 * counts and labels for every filter dimension.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchFacetsDto {
    @Builder.Default
    private List<FacetItemDto> subcategories = Collections.emptyList();
    @Builder.Default
    private List<FacetItemDto> cities = Collections.emptyList();
    @Builder.Default
    private List<FacetItemDto> ratings = Collections.emptyList();
    @Builder.Default
    private List<FacetItemDto> services = Collections.emptyList();
}
