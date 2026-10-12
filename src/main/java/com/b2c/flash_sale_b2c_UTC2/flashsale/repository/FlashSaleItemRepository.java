package com.b2c.flash_sale_b2c_UTC2.flashsale.repository;

import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
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
}
