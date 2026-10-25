package com.b2c.flash_sale_b2c_UTC2.search.imagesearch.repository;

import com.b2c.flash_sale_b2c_UTC2.search.imagesearch.entity.ImageEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ImageEmbeddingRepository extends JpaRepository<ImageEmbedding, Long> {

    /**
     * Thêm mới hoặc cập nhật vector embedding cho ảnh bằng Native Query.
     * Cú pháp CAST(:embedding AS vector) tương thích chuẩn với extension pgvector.
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO image_embeddings (image_id, product_id, embedding, created_at, updated_at)
        VALUES (:imageId, :productId, CAST(:embedding AS vector), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (image_id) DO UPDATE
        SET product_id = EXCLUDED.product_id,
            embedding = EXCLUDED.embedding,
            updated_at = CURRENT_TIMESTAMP
        """, nativeQuery = true)
    int upsertEmbedding(
            @Param("imageId") Long imageId,
            @Param("productId") Long productId,
            @Param("embedding") String embedding
    );

    /**
     * Tìm kiếm top sản phẩm tương đồng nhất dựa trên Cosine Distance (<=> operator).
     * Chỉ lấy các sản phẩm ACTIVE có cửa hàng APPROVED.
     */
    @Query(value = """
        SELECT ie.product_id AS productId,
               MIN(ie.embedding <=> CAST(:queryVector AS vector)) AS distance
        FROM image_embeddings ie
        JOIN products p ON ie.product_id = p.id
        JOIN stores s ON p.store_id = s.id
        WHERE p.status = 'ACTIVE'
          AND s.status = 'APPROVED'
          AND (ie.embedding <=> CAST(:queryVector AS vector)) <= :maxDistance
        GROUP BY ie.product_id
        ORDER BY distance ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<ProductSimilarityProjection> findSimilarProducts(
            @Param("queryVector") String queryVector,
            @Param("maxDistance") double maxDistance,
            @Param("limit") int limit
    );
}
