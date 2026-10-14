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
public class ProductSearchFacetsDto {
    private List<CategoryFacetDto> categories;
    private List<PriceRangeFacetDto> priceRanges;
    private List<String> services;
    private List<Integer> ratings;
}
