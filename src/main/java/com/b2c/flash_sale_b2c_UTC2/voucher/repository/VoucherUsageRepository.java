package com.b2c.flash_sale_b2c_UTC2.voucher.repository;

import com.b2c.flash_sale_b2c_UTC2.voucher.entity.VoucherUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, Long> {
    Optional<VoucherUsage> findByOrderId(Long orderId);
    List<VoucherUsage> findByUserId(Long userId);
    long countByVoucherIdAndUserId(Long voucherId, Long userId);

    @org.springframework.data.jpa.repository.Query("SELECT vu.voucher.id, COUNT(vu.id) FROM VoucherUsage vu WHERE vu.user.id = :userId AND vu.voucher.id IN :voucherIds GROUP BY vu.voucher.id")
    List<Object[]> countByUserIdAndVoucherIdIn(@org.springframework.data.repository.query.Param("userId") Long userId, @org.springframework.data.repository.query.Param("voucherIds") List<Long> voucherIds);
}
