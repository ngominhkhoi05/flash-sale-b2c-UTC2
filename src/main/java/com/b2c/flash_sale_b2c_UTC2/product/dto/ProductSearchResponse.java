package com.b2c.flash_sale_b2c_UTC2.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchResponse {
    private List<ProductSearchItemDto> items;
    private Long total;
    private Integer page;
    private Integer pageSize;
    private ProductSearchFacetsDto facets;
    private ProductSearchMallDto mall;
}
