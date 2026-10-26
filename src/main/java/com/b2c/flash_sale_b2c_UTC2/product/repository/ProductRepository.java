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
import java.time.Instant;
import java.util.Collection;
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

    /**
     * Load products by id with store + category, giữ nguyên thứ tự id truyền vào
     * (dùng cho hydrate kết quả search — mô hình search-then-hydrate).
     *
     * @param ids danh sách id sản phẩm.
     * @return danh sách product (có thể rỗng nếu không id nào tồn tại).
     */
    @Query("SELECT p FROM Product p JOIN FETCH p.store s JOIN FETCH p.category c WHERE p.id IN :ids")
    List<Product> findAllByIdInWithStoreAndCategory(@Param("ids") Collection<Long> ids);

    /**
     * Load sản phẩm ACTIVE của store APPROVED theo id — lớp phòng thủ thứ 2 cho
     * search-then-hydrate (OpenSearch đã filter {@code status=ACTIVE} ở layer 1).
     *
     * @param ids danh sách id sản phẩm.
     * @return danh sách product đủ điều kiện hiển thị.
     */
    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.store s
        JOIN FETCH p.category c
        WHERE p.id IN :ids
        AND p.status = 'ACTIVE'
        AND s.status = 'APPROVED'
        """)
    List<Product> findAllActiveByIdInWithStoreAndCategory(@Param("ids") Collection<Long> ids);

    /**
     * Sản phẩm ACTIVE (store APPROVED) đã cập nhật sau mốc thời gian cho trước —
     * dùng cho reconcile định kỳ của index OpenSearch.
     *
     * @param since mốc thời gian (exclusive).
     * @param limit số lượng tối đa trả về (batch nhỏ để an toàn multi-instance).
     * @return danh sách product thay đổi.
     */
    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.store s
        JOIN FETCH p.category c
        WHERE p.status = 'ACTIVE'
        AND s.status = 'APPROVED'
        AND p.updatedAt > :since
        ORDER BY p.updatedAt ASC
        """)
    List<Product> findActiveUpdatedAfter(@Param("since") Instant since, Pageable pageable);

    /**
     * Tất cả sản phẩm ACTIVE (store APPROVED) — dùng cho job full-reindex.
     *
     * @param pageable phân trang theo id tăng dần (batch nhỏ).
     * @return danh sách product.
     */
    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.store s
        JOIN FETCH p.category c
        WHERE p.status = 'ACTIVE'
        AND s.status = 'APPROVED'
        ORDER BY p.id ASC
        """)
    List<Product> findAllActiveWithStoreAndCategory(Pageable pageable);

    /**
     * Tất cả id sản phẩm đã tồn tại trong DB (dùng để dọn document mồ côi trong
     * index OpenSearch — sản phẩm đã hard delete).
     *
     * @return tập id sản phẩm.
     */
    @Query("SELECT p.id FROM Product p")
    List<Long> findAllIds();

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

    @Query("SELECT p.store.id, count(p) FROM Product p WHERE p.store.id IN :storeIds GROUP BY p.store.id")
    List<Object[]> countProductsByStoreIds(@Param("storeIds") List<Long> storeIds);

    /**
     * Admin product list: status + categoryId + search, paged, with eager store + category.
     * Mirrors {@link #findSellerProducts(Long, String, Pageable)} but admin-side: searches across
     * all stores and includes a LIKE-name filter.
     *
     * <p>Filters are optional; null/empty/0 means "do not apply". Caller is expected to pre-trim
     * string inputs (see {@code ProductServiceImpl.listAdminProducts}).
     */
    @Query(value = """
        SELECT p FROM Product p
        JOIN FETCH p.store s
        JOIN FETCH p.category c
        WHERE (:status IS NULL OR :status = '' OR p.status = :status)
        AND (:categoryId IS NULL OR c.id = :categoryId)
        AND (:search IS NULL OR :search = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))
        """,
        countQuery = """
        SELECT count(p) FROM Product p
        WHERE (:status IS NULL OR :status = '' OR p.status = :status)
        AND (:categoryId IS NULL OR p.category.id = :categoryId)
        AND (:search IS NULL OR :search = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<Product> findAdminProducts(
            @Param("status") String status,
            @Param("categoryId") Integer categoryId,
            @Param("search") String search,
            Pageable pageable
    );

    boolean existsByCategoryId(Integer categoryId);
}
