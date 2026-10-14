package com.b2c.flash_sale_b2c_UTC2.product.service;

import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductSearchRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductSearchResponse;

public interface ProductSearchService {
    ProductSearchResponse searchProducts(ProductSearchRequest request);
}
