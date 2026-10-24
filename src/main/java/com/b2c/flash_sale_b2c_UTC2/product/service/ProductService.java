package com.b2c.flash_sale_b2c_UTC2.product.service;

import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.CreateProductRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductDetailResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductFilterRequest;
import com.b2c.flash_sale_b2c_UTC2.product.dto.ProductSummaryResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.RecommendedProductsResponse;
import com.b2c.flash_sale_b2c_UTC2.product.dto.UpdateProductRequest;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    RecommendedProductsResponse getRecommendedProducts(String tab, Long categoryId, int limit);

    ProductDetailResponse createProduct(Long userId, CreateProductRequest request);

    ProductDetailResponse updateProduct(Long userId, Long productId, UpdateProductRequest request);

    void deleteProduct(Long userId, Long productId);

    ProductDetailResponse updateProductStatus(Long userId, Long productId, String status);

    PageResponse<ProductSummaryResponse> getPublicProducts(ProductFilterRequest filter, Pageable pageable);

    ProductDetailResponse getPublicProductDetail(Long productId);

    ProductDetailResponse getPublicProductDetailBySlug(String slug);

    PageResponse<ProductSummaryResponse> getSellerProducts(Long userId, String status, Pageable pageable);

    ProductDetailResponse getSellerProductDetail(Long userId, Long productId);

    PageResponse<com.b2c.flash_sale_b2c_UTC2.product.dto.AdminProductResponse> getAdminProducts(
            String status,
            Integer categoryId,
            String search,
            Pageable pageable
    );

    com.b2c.flash_sale_b2c_UTC2.product.dto.AdminProductResponse updateProductStatusForAdmin(
            Long productId,
            com.b2c.flash_sale_b2c_UTC2.product.dto.UpdateProductStatusRequest request
    );
}
