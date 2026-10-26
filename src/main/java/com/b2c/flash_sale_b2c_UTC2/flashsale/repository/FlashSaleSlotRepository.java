package com.b2c.flash_sale_b2c_UTC2.flashsale.repository;

import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface FlashSaleSlotRepository extends JpaRepository<FlashSaleSlot, Long> {

    List<FlashSaleSlot> findByStatusOrderByStartTimeAsc(String status);

    @Query("SELECT s FROM FlashSaleSlot s WHERE s.status IN :statuses ORDER BY s.startTime ASC")
    List<FlashSaleSlot> findByStatusInOrderByStartTimeAsc(@Param("statuses") List<String> statuses);

    @Query("""
        SELECT COUNT(s) > 0 FROM FlashSaleSlot s
        WHERE (:excludeId IS NULL OR s.id != :excludeId)
        AND s.status != 'ENDED'
        AND s.startTime < :endTime
        AND s.endTime > :startTime
    """)
    boolean existsOverlappingSlot(
            @Param("excludeId") Long excludeId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );

    @Query("SELECT s FROM FlashSaleSlot s WHERE s.status IN ('UPCOMING', 'REGISTRATION_OPEN') AND s.startTime <= :now AND s.endTime > :now")
    List<FlashSaleSlot> findSlotsToActivate(@Param("now") Instant now);

    @Query("SELECT s FROM FlashSaleSlot s WHERE s.status != 'ENDED' AND s.endTime <= :now")
    List<FlashSaleSlot> findSlotsToEnd(@Param("now") Instant now);

}
