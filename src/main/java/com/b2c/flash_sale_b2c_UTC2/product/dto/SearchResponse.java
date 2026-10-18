package com.b2c.flash_sale_b2c_UTC2.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

/**
 * Response envelope for {@code GET /api/v1/products/search}.
 *
 * <p>Differs from {@link PageResponse} in two ways:</p>
 * <ul>
 *   <li>Uses 0-based {@code page} to match the rest of the public product API.</li>
 *   <li>Carries {@link SearchFacetsDto} alongside items so the FE can render
 *       sidebar filters without a second round-trip.</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {
    @Builder.Default
    private List<ProductSummaryResponse> items = Collections.emptyList();
    private long total;
    private int page;
    private int pageSize;
    private SearchFacetsDto facets;
}
