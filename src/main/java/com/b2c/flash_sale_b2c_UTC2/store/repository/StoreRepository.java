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

    /**
     * Find featured stores (approved status). Order by creation date DESC.
     * Used by GET /api/v1/stores/featured endpoint.
     */
    @Query("SELECT s FROM Store s WHERE s.status = 'APPROVED' ORDER BY s.createdAt DESC")
    List<Store> findFeaturedStores(Pageable pageable);
}
