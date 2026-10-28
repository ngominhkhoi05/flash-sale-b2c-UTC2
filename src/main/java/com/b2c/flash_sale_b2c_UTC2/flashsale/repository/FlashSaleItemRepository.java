package com.b2c.flash_sale_b2c_UTC2.flashsale.repository;

import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FlashSaleItemRepository extends JpaRepository<FlashSaleItem, Long> {

    Optional<FlashSaleItem> findBySlotIdAndVariantId(Long slotId, Long variantId);

    List<FlashSaleItem> findBySlotIdAndStatus(Long slotId, String status);

    List<FlashSaleItem> findBySlotId(Long slotId);
    long countByStatus(String status);

    @Query("""
        SELECT fsi FROM FlashSaleItem fsi
        JOIN FETCH fsi.variant v
        JOIN FETCH v.product p
        WHERE fsi.slot.id = :slotId
        AND fsi.status = 'APPROVED'
    """)
    List<FlashSaleItem> findApprovedItemsWithDetailsBySlotId(@Param("slotId") Long slotId);

    boolean existsBySlotIdAndVariantId(Long slotId, Long variantId);

    boolean existsByVariantIdIn(List<Long> variantIds);

    @Query("""
        SELECT count(fsi) > 0 FROM FlashSaleItem fsi
        WHERE fsi.variant.id = :variantId
        AND fsi.status = 'APPROVED'
        AND fsi.slot.status = 'ACTIVE'
    """)
    boolean isVariantInActiveFlashSale(@Param("variantId") Long variantId);

    @Query("""
        SELECT count(fsi) > 0 FROM FlashSaleItem fsi
        WHERE fsi.variant.id IN :variantIds
        AND fsi.status = 'APPROVED'
        AND fsi.slot.status = 'ACTIVE'
    """)
    boolean areAnyVariantsInActiveFlashSale(@Param("variantIds") List<Long> variantIds);

    @Modifying
    @Query("""
        UPDATE FlashSaleItem fsi
        SET fsi.availableStock = fsi.availableStock - :quantity
        WHERE fsi.id = :id
        AND fsi.availableStock >= :quantity
    """)
    int deductAvailableStockConditionally(@Param("id") Long id, @Param("quantity") Integer quantity);

    @Modifying
    @Query("""
        UPDATE FlashSaleItem fsi
        SET fsi.availableStock = fsi.availableStock + :quantity
        WHERE fsi.id = :id
        AND fsi.availableStock + :quantity <= fsi.allocatedStock
    """)
    int replenishAvailableStockConditionally(@Param("id") Long id, @Param("quantity") Integer quantity);

    @Query("""
        SELECT fsi FROM FlashSaleItem fsi
        JOIN FETCH fsi.variant v
        JOIN FETCH v.product p
        JOIN FETCH p.store s
        JOIN FETCH p.category c
        WHERE fsi.slot.status = 'ACTIVE'
        AND fsi.status = 'APPROVED'
        AND fsi.availableStock > 0
        ORDER BY fsi.availableStock DESC
        """)
    List<FlashSaleItem> findHotDealItems(Pageable pageable);

    @Query("SELECT fsi FROM FlashSaleItem fsi JOIN FETCH fsi.slot sl JOIN FETCH fsi.variant v WHERE v.product.id = :productId AND sl.status = 'ACTIVE' AND fsi.status = 'APPROVED' ORDER BY fsi.flashSalePrice ASC")
    List<FlashSaleItem> findActiveByProductId(@Param("productId") Long productId);

    /**
     * Count distinct active flash sale items (APPROVED + slot ACTIVE) for a given store.
     * Counts distinct slot IDs that are currently active for products belonging to the store.
     */
    @Query("""
        SELECT COUNT(DISTINCT fsi.slot.id)
        FROM FlashSaleItem fsi
        JOIN fsi.variant v
        JOIN fsi.slot s
        WHERE v.product.store.id = :storeId
        AND fsi.status = 'APPROVED'
        AND s.status = 'ACTIVE'
    """)
    long countActiveByStoreId(@Param("storeId") Long storeId);

    @Query(value = """
        SELECT fsi FROM FlashSaleItem fsi
        JOIN FETCH fsi.slot s
        JOIN FETCH fsi.variant v
        JOIN FETCH v.product p
        JOIN FETCH p.store st
        WHERE st.id = :storeId
        AND (:slotId IS NULL OR s.id = :slotId)
        AND (:status IS NULL OR :status = '' OR fsi.status = :status)
    """,
    countQuery = """
        SELECT COUNT(fsi) FROM FlashSaleItem fsi
        JOIN fsi.slot s
        JOIN fsi.variant v
        JOIN v.product p
        JOIN p.store st
        WHERE st.id = :storeId
        AND (:slotId IS NULL OR s.id = :slotId)
        AND (:status IS NULL OR :status = '' OR fsi.status = :status)
    """)
    Page<FlashSaleItem> findByStoreIdAndFilters(
            @Param("storeId") Long storeId,
            @Param("slotId") Long slotId,
            @Param("status") String status,
            Pageable pageable
    );

    @Query(value = """
        SELECT fsi FROM FlashSaleItem fsi
        JOIN FETCH fsi.slot s
        JOIN FETCH fsi.variant v
        JOIN FETCH v.product p
        JOIN FETCH p.store st
        WHERE (:slotId IS NULL OR s.id = :slotId)
        AND (:status IS NULL OR :status = '' OR fsi.status = :status)
        AND (
            :search IS NULL OR :search = '' OR
            LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(st.storeName) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(v.sku) LIKE LOWER(CONCAT('%', :search, '%'))
        )
    """,
    countQuery = """
        SELECT COUNT(fsi) FROM FlashSaleItem fsi
        JOIN fsi.slot s
        JOIN fsi.variant v
        JOIN v.product p
        JOIN p.store st
        WHERE (:slotId IS NULL OR s.id = :slotId)
        AND (:status IS NULL OR :status = '' OR fsi.status = :status)
        AND (
            :search IS NULL OR :search = '' OR
            LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(st.storeName) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(v.sku) LIKE LOWER(CONCAT('%', :search, '%'))
        )
    """)
    Page<FlashSaleItem> findBySlotIdAndFilters(
            @Param("slotId") Long slotId,
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
        SELECT fsi.status, COUNT(fsi)
        FROM FlashSaleItem fsi
        WHERE (:slotId IS NULL OR fsi.slot.id = :slotId)
        GROUP BY fsi.status
    """)
    List<Object[]> countStatusBySlotId(@Param("slotId") Long slotId);

    /**
     * Danh sách product id đang có flash sale ACTIVE (item APPROVED + slot ACTIVE).
     *
     * <p>Dùng để denormalize cờ {@code isFlashSale} vào document OpenSearch.
     *
     * @param productIds danh sách product id cần kiểm tra.
     * @return danh sách product id có flash sale ACTIVE.
     */
    @Query("""
        SELECT DISTINCT v.product.id
        FROM FlashSaleItem fsi
        JOIN fsi.variant v
        JOIN fsi.slot sl
        WHERE v.product.id IN :productIds
        AND fsi.status = 'APPROVED'
        AND sl.status = 'ACTIVE'
        """)
    List<Long> findProductIdsWithActiveFlashSale(@Param("productIds") java.util.Collection<Long> productIds);

    @Query("""
        SELECT 
            fsi.slot.id,
            SUM(CASE WHEN fsi.status = 'APPROVED' THEN 1L ELSE 0L END),
            SUM(CASE WHEN fsi.status = 'PENDING_APPROVAL' OR fsi.status = 'PENDING' THEN 1L ELSE 0L END),
            COUNT(fsi),
            COALESCE(SUM(CASE WHEN fsi.status = 'APPROVED' THEN CAST(fsi.allocatedStock AS long) ELSE 0L END), 0L),
            COALESCE(SUM(CASE WHEN fsi.status = 'APPROVED' THEN CAST(fsi.allocatedStock - fsi.availableStock AS long) ELSE 0L END), 0L)
        FROM FlashSaleItem fsi
        GROUP BY fsi.slot.id
    """)
    List<Object[]> getSlotSkuStats();
}
