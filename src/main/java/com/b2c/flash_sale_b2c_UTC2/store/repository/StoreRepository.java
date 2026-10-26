package com.b2c.flash_sale_b2c_UTC2.store.repository;

import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {
    Optional<Store> findByUserId(Long userId);
    Optional<Store> findByStoreName(String storeName);
    boolean existsByStoreName(String storeName);
    boolean existsByUserId(Long userId);
    long countByCreatedAtBetween(java.time.Instant start, java.time.Instant end);
    long countByStatus(String status);

    /**
     * Find featured stores (approved status). Order by creation date DESC.
     * Used by GET /api/v1/stores/featured endpoint.
     */
    @Query("SELECT s FROM Store s WHERE s.status = 'APPROVED' ORDER BY s.createdAt DESC")
    List<Store> findFeaturedStores(Pageable pageable);

    /**
     * Find stores for Admin with optional status and search filters (store name, owner name/email/phone).
     */
    @Query(value = """
        SELECT s FROM Store s
        JOIN FETCH s.user u
        WHERE (:status IS NULL OR :status = '' OR s.status = :status)
        AND (
            :search IS NULL OR :search = '' OR
            LOWER(s.storeName) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR
            (u.phone IS NOT NULL AND u.phone LIKE CONCAT('%', :search, '%'))
        )
        ORDER BY s.createdAt DESC
    """,
    countQuery = """
        SELECT count(s) FROM Store s
        JOIN s.user u
        WHERE (:status IS NULL OR :status = '' OR s.status = :status)
        AND (
            :search IS NULL OR :search = '' OR
            LOWER(s.storeName) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR
            (u.phone IS NOT NULL AND u.phone LIKE CONCAT('%', :search, '%'))
        )
    """)
    org.springframework.data.domain.Page<Store> findAdminStores(
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable
    );
}
