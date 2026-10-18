package com.b2c.flash_sale_b2c_UTC2.order.repository;

import com.b2c.flash_sale_b2c_UTC2.order.entity.Order;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderCode(String orderCode);
    Page<Order> findByBuyerId(Long buyerId, Pageable pageable);
    Page<Order> findByBuyerIdAndStatus(Long buyerId, String status, Pageable pageable);
    Page<Order> findByStoreId(Long storeId, Pageable pageable);
    Page<Order> findByStoreIdAndStatus(Long storeId, String status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
    @Query("SELECT o FROM Order o WHERE o.status = 'PENDING_PAYMENT' AND o.expiresAt < :now AND o.slot IS NOT NULL")
    List<Order> findExpiredPendingOrdersWithLock(@Param("now") Instant now, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.status = 'PENDING_PAYMENT' AND o.expiresAt < :now AND o.slot IS NOT NULL")
    List<Order> findExpiredPendingOrders(@Param("now") Instant now, Pageable pageable);

    @Modifying
    @Query("UPDATE Order o SET o.status = 'CANCELLED_TIMEOUT', o.updatedAt = :now WHERE o.id = :orderId AND o.status = 'PENDING_PAYMENT'")
    int cancelTimeoutIfPending(@Param("orderId") Long orderId, @Param("now") Instant now);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.slot.id = :slotId AND o.status = 'PENDING_PAYMENT'")
    long countPendingBySlotId(@Param("slotId") Long slotId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.slot.id = :slotId AND o.status NOT IN ('PENDING_PAYMENT', 'CANCELLED', 'CANCELLED_TIMEOUT')")
    long countSuccessfulOrdersBySlotId(@Param("slotId") Long slotId);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.slot.id = :slotId AND o.status NOT IN ('PENDING_PAYMENT', 'CANCELLED', 'CANCELLED_TIMEOUT')")
    BigDecimal sumTotalRevenueBySlotId(@Param("slotId") Long slotId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.slot.id = :slotId AND o.status = 'CANCELLED_TIMEOUT'")
    long countExpiredOrdersBySlotId(@Param("slotId") Long slotId);

    /**
     * Count orders by store ID and status.
     */
    long countByStoreIdAndStatus(Long storeId, String status);

    /**
     * Count orders by store ID created within a time range.
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.store.id = :storeId AND o.createdAt >= :start AND o.createdAt < :end")
    long countByStoreIdAndCreatedAtBetween(
            @Param("storeId") Long storeId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    /**
     * Sum total amount of orders by store ID created within a time range and with given statuses.
     */
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.store.id = :storeId AND o.createdAt >= :start AND o.createdAt < :end AND o.status IN :statuses")
    BigDecimal sumTotalAmountByStoreIdAndCreatedAtBetweenAndStatusIn(
            @Param("storeId") Long storeId,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("statuses") List<String> statuses);

    @Query("SELECT o.store.id, count(o) FROM Order o WHERE o.store.id IN :storeIds GROUP BY o.store.id")
    List<Object[]> countOrdersByStoreIds(@Param("storeIds") List<Long> storeIds);

    /**
     * Count orders grouped by buyerId (for admin user list sidebar summary).
     * Returns rows of {@code [buyerId(Long), orderCount(Long)]} — buyers with zero orders are
     * NOT included; caller must default missing keys to 0.
     *
     * <p>Used by {@code UserServiceImpl.listAdminUsers} to batch-load order counts for many
     * users at once, avoiding N+1.
     */
    @Query("SELECT o.buyer.id, count(o) FROM Order o WHERE o.buyer.id IN :buyerIds GROUP BY o.buyer.id")
    List<Object[]> countOrdersByBuyerIds(@Param("buyerIds") List<Long> buyerIds);
}
