package com.b2c.flash_sale_b2c_UTC2.product.repository;

import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    Optional<ProductVariant> findBySku(String sku);
    List<ProductVariant> findByProductId(Long productId);
    List<ProductVariant> findByProductIdAndStatus(Long productId, String status);
    List<ProductVariant> findByProductIdInAndStatus(List<Long> productIds, String status);
    List<ProductVariant> findByProductIdIn(List<Long> productIds);
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, Long id);

    @Modifying
    @Query("UPDATE ProductVariant v SET v.stockQuantity = :newStock, v.version = v.version + 1 WHERE v.id = :id AND v.stockQuantity = :expectedOldStock")
    int updateStockQuantityConditionally(@Param("id") Long id, @Param("newStock") Integer newStock, @Param("expectedOldStock") Integer expectedOldStock);

    @Modifying
    @Query("UPDATE ProductVariant v SET v.stockQuantity = v.stockQuantity - :quantity, v.version = v.version + 1 WHERE v.id = :id AND v.stockQuantity >= :quantity")
    int deductStockQuantityConditionally(@Param("id") Long id, @Param("quantity") Integer quantity);

    @Modifying
    @Query("UPDATE ProductVariant v SET v.stockQuantity = v.stockQuantity + :quantity, v.version = v.version + 1 WHERE v.id = :id")
    int replenishStockQuantityConditionally(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * Count variants with stock quantity below a threshold for all products of a store.
     */
    @Query("SELECT COUNT(v) FROM ProductVariant v WHERE v.product.store.id = :storeId AND v.stockQuantity < :threshold")
    long countByProductStoreIdAndStockQuantityLessThan(@Param("storeId") Long storeId, @Param("threshold") Integer threshold);
}
