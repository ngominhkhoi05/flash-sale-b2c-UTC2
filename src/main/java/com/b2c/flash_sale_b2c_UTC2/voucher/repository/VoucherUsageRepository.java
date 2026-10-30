package com.b2c.flash_sale_b2c_UTC2.voucher.repository;

import com.b2c.flash_sale_b2c_UTC2.voucher.entity.VoucherUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, Long> {
    Optional<VoucherUsage> findByOrderId(Long orderId);
    List<VoucherUsage> findByUserId(Long userId);
    long countByVoucherIdAndUserId(Long voucherId, Long userId);
    boolean existsByVoucherId(Long voucherId);

    @Query("SELECT vu FROM VoucherUsage vu JOIN FETCH vu.user JOIN FETCH vu.order WHERE vu.voucher.id = :voucherId ORDER BY vu.usedAt DESC")
    List<VoucherUsage> findByVoucherIdWithDetails(@Param("voucherId") Long voucherId);

    @Query(value = "SELECT vu FROM VoucherUsage vu JOIN FETCH vu.user JOIN FETCH vu.order WHERE vu.voucher.id = :voucherId",
           countQuery = "SELECT COUNT(vu) FROM VoucherUsage vu WHERE vu.voucher.id = :voucherId")
    Page<VoucherUsage> findByVoucherIdWithDetails(@Param("voucherId") Long voucherId, Pageable pageable);

    @Query("SELECT vu.voucher.id, COUNT(vu.id) FROM VoucherUsage vu WHERE vu.user.id = :userId AND vu.voucher.id IN :voucherIds GROUP BY vu.voucher.id")
    List<Object[]> countByUserIdAndVoucherIdIn(@Param("userId") Long userId, @Param("voucherIds") List<Long> voucherIds);

    @Query("SELECT COALESCE(SUM(vu.discountAmount), 0) FROM VoucherUsage vu")
    BigDecimal sumTotalUsedBudget();
}
