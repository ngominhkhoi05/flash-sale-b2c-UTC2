package com.b2c.flash_sale_b2c_UTC2.product.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendedProductsResponse {

    private List<RecommendedProductResponse> forYou;
    private List<RecommendedProductResponse> bestSellers;
    private List<RecommendedProductResponse> hotDeals;
}
