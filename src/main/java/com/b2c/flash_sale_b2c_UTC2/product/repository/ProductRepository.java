package com.b2c.flash_sale_b2c_UTC2.product.repository;

import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @Query(value = """
        SELECT DISTINCT p FROM Product p
        JOIN FETCH p.store s
        JOIN FETCH p.category c
        WHERE p.status = 'ACTIVE'
        AND s.status = 'APPROVED'
        AND (:categoryId < 0 OR c.id = :categoryId)
        AND (:keyword = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        AND (:minPrice < 0 OR EXISTS (
            SELECT 1 FROM ProductVariant v WHERE v.product.id = p.id AND v.status = 'ACTIVE' AND v.originalPrice >= :minPrice
        ))
        AND (:maxPrice < 0 OR EXISTS (
            SELECT 1 FROM ProductVariant v WHERE v.product.id = p.id AND v.status = 'ACTIVE' AND v.originalPrice <= :maxPrice
        ))
    """,
    countQuery = """
        SELECT count(DISTINCT p) FROM Product p
        WHERE p.status = 'ACTIVE'
        AND p.store.status = 'APPROVED'
        AND (:categoryId < 0 OR p.category.id = :categoryId)
        AND (:keyword = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        AND (:minPrice < 0 OR EXISTS (
            SELECT 1 FROM ProductVariant v WHERE v.product.id = p.id AND v.status = 'ACTIVE' AND v.originalPrice >= :minPrice
        ))
        AND (:maxPrice < 0 OR EXISTS (
            SELECT 1 FROM ProductVariant v WHERE v.product.id = p.id AND v.status = 'ACTIVE' AND v.originalPrice <= :maxPrice
        ))
    """)
    Page<Product> findPublicProducts(
            @Param("categoryId") Integer categoryId,
            @Param("keyword") String keyword,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable
    );

    @Query(value = """
        SELECT p FROM Product p
        JOIN FETCH p.store s
        JOIN FETCH p.category c
        WHERE s.id = :storeId
        AND (:status IS NULL OR p.status = :status)
    """,
    countQuery = """
        SELECT count(p) FROM Product p
        WHERE p.store.id = :storeId
        AND (:status IS NULL OR p.status = :status)
    """)
    Page<Product> findSellerProducts(
            @Param("storeId") Long storeId,
            @Param("status") String status,
            Pageable pageable
    );

        @Query("SELECT p FROM Product p JOIN FETCH p.store s JOIN FETCH p.category c WHERE p.slug = :slug")
    Optional<Product> findBySlugWithStoreAndCategory(@Param("slug") String slug);

    @Query("SELECT p FROM Product p JOIN FETCH p.store s JOIN FETCH p.category c WHERE p.id = :id")
    Optional<Product> findByIdWithStoreAndCategory(@Param("id") Long id);

    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.store s
        JOIN FETCH p.category c
        WHERE p.status = 'ACTIVE'
        AND s.status = 'APPROVED'
        ORDER BY p.createdAt DESC
        """)
    List<Product> findForYouProducts(Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.store s
        JOIN FETCH p.category c
        WHERE p.status = 'ACTIVE'
        AND s.status = 'APPROVED'
        ORDER BY p.totalSold DESC
        """)
    List<Product> findBestSellerProducts(Pageable pageable);

    @Modifying
    @Query("UPDATE Product p SET p.totalSold = p.totalSold + :quantity WHERE p.id = :productId")
    void incrementTotalSold(@Param("productId") Long productId, @Param("quantity") int quantity);

    @Query("SELECT MIN(v.originalPrice), MAX(v.originalPrice) FROM ProductVariant v WHERE v.product.id = :productId AND v.status = 'ACTIVE'")
    Object[] findPriceRangeByProductId(@Param("productId") Long productId);

    boolean existsByIdAndStatus(Long id, String status);

    /**
     * Count active products for a store.
     */
    long countByStoreIdAndStatus(Long storeId, String status);

    /**
     * Sort by price ascending: uses COALESCE MIN of variant prices.
     * Must be used with JpaSort.unsafe() since MIN is an aggregate.
     */
    @Query("SELECT p FROM Product p JOIN FETCH p.store s JOIN FETCH p.category c " +
           "WHERE p.status = 'ACTIVE' AND s.status = 'APPROVED' " +
           "ORDER BY (SELECT MIN(v.originalPrice) FROM ProductVariant v WHERE v.product.id = p.id AND v.status = 'ACTIVE') ASC")
    List<Product> findAllOrderByMinPriceAsc(Pageable pageable);

    /**
     * Sort by price descending: uses COALESCE MAX of variant prices.
     */
    @Query("SELECT p FROM Product p JOIN FETCH p.store s JOIN FETCH p.category c " +
           "WHERE p.status = 'ACTIVE' AND s.status = 'APPROVED' " +
           "ORDER BY (SELECT MAX(v.originalPrice) FROM ProductVariant v WHERE v.product.id = p.id AND v.status = 'ACTIVE') DESC")
    List<Product> findAllOrderByMinPriceDesc(Pageable pageable);

    /**
     * Products that have at least one active flash sale item.
     */
    @Query("SELECT DISTINCT p FROM Product p JOIN p.store s " +
           "WHERE p.status = 'ACTIVE' AND s.status = 'APPROVED' " +
           "AND EXISTS (" +
           "  SELECT 1 FROM com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem fsi " +
           "  JOIN fsi.variant v JOIN fsi.slot sl " +
           "  WHERE v.product.id = p.id AND fsi.status = 'APPROVED' AND sl.status = 'ACTIVE'" +
           ")")
    Page<Product> findProductsWithActiveFlashSale(Pageable pageable);

    /**
     * Products with at least one active flash sale item, with categoryId filter.
     */
    @Query("SELECT DISTINCT p FROM Product p JOIN p.store s JOIN p.category c " +
           "WHERE p.status = 'ACTIVE' AND s.status = 'APPROVED' " +
           "AND (:categoryId < 0 OR c.id = :categoryId) " +
           "AND EXISTS (" +
           "  SELECT 1 FROM com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem fsi " +
           "  JOIN fsi.variant v JOIN fsi.slot sl " +
           "  WHERE v.product.id = p.id AND fsi.status = 'APPROVED' AND sl.status = 'ACTIVE'" +
           ")")
    Page<Product> findProductsWithActiveFlashSaleByCategory(@Param("categoryId") Integer categoryId, Pageable pageable);
}
