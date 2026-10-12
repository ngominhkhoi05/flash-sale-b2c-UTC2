package com.b2c.flash_sale_b2c_UTC2.review.repository;

import com.b2c.flash_sale_b2c_UTC2.review.entity.ProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    Optional<ProductReview> findByOrderItemId(Long orderItemId);
    Page<ProductReview> findByProductIdAndStatus(Long productId, String status, Pageable pageable);
    Page<ProductReview> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM ProductReview r WHERE r.product.id = :productId AND r.status = 'VISIBLE'")
    Double avgRatingByProductId(@Param("productId") Long productId);

    @Query("SELECT count(r) FROM ProductReview r WHERE r.product.id = :productId AND r.status = 'VISIBLE'")
    long countByProductIdAndVisible(@Param("productId") Long productId);

    /**
     * Tìm reviews theo product + status + optional rating + optional hasMedia filter.
     * Logic hasMedia=true cần JOIN với images table để tìm review có ảnh.
     * Khi hasMedia=null thì không filter theo ảnh.
     */
    @Query("""
        SELECT DISTINCT r FROM ProductReview r
        LEFT JOIN FETCH r.orderItem oi
        LEFT JOIN FETCH oi.variant v
        WHERE r.product.id = :productId
        AND r.status = :status
        AND (:rating IS NULL OR r.rating = :rating)
        AND (:hasMedia IS NULL OR (
            :hasMedia = true AND EXISTS (
                SELECT 1 FROM com.b2c.flash_sale_b2c_UTC2.image.entity.Image i WHERE i.ownerType = 'REVIEW' AND i.ownerId = r.id AND i.status = 'ACTIVE'
            )
        ))
        ORDER BY r.createdAt DESC
        """)
    Page<ProductReview> findByProductIdAndStatusAndRatingAndHasMedia(
            @Param("productId") Long productId,
            @Param("status") String status,
            @Param("rating") Integer rating,
            @Param("hasMedia") Boolean hasMedia,
            Pageable pageable);

    /**
     * Đếm số review theo từng mức rating cho một product.
     * Trả về danh sách [rating, count] cho mỗi mức có review.
     */
    @Query("SELECT r.rating, count(r) FROM ProductReview r WHERE r.product.id = :productId AND r.status = 'VISIBLE' GROUP BY r.rating")
    List<Object[]> countByRatingGrouped(@Param("productId") Long productId);

    /**
     * Đếm tổng số review có ảnh (JOIN với bảng images, owner_type='REVIEW').
     * Không phụ thuộc vào filter hasMedia — dùng cho mediaCount luôn.
     */
    @Query("SELECT count(DISTINCT r) FROM ProductReview r JOIN com.b2c.flash_sale_b2c_UTC2.image.entity.Image i ON i.ownerType = 'REVIEW' AND i.ownerId = r.id WHERE r.product.id = :productId AND r.status = 'VISIBLE' AND i.status = 'ACTIVE'")
    long countByProductIdWithMedia(@Param("productId") Long productId);

    /**
     * Average rating for all visible reviews of all products belonging to a store.
     * Returns null if no reviews exist.
     */
    @Query("SELECT AVG(r.rating) FROM ProductReview r WHERE r.product.store.id = :storeId AND r.status = 'VISIBLE'")
    Double avgRatingByStoreId(@Param("storeId") Long storeId);

    /**
     * Count of visible reviews for all products belonging to a store.
     */
    @Query("SELECT COUNT(r) FROM ProductReview r WHERE r.product.store.id = :storeId AND r.status = 'VISIBLE'")
    long countByStoreId(@Param("storeId") Long storeId);
}
