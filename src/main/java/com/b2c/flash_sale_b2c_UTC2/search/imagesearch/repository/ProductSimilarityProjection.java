package com.b2c.flash_sale_b2c_UTC2.search.imagesearch.repository;

/**
 * Projection chứa productId và khoảng cách Cosine Distance trả về từ pgvector.
 */
public interface ProductSimilarityProjection {

    Long getProductId();

    Double getDistance();
}
