package com.b2c.flash_sale_b2c_UTC2.voucher.repository;

import com.b2c.flash_sale_b2c_UTC2.voucher.entity.Voucher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    Optional<Voucher> findByCode(String code);
    Optional<Voucher> findByCodeIgnoreCase(String code);
    boolean existsByCode(String code);
    List<Voucher> findByStoreId(Long storeId);
    List<Voucher> findByStoreIdIsNull();

    @Query("SELECT v FROM Voucher v WHERE v.store.id = :storeId " +
           "AND (:status IS NULL OR :status = 'ALL' " +
           "     OR (:status = 'ACTIVE' AND v.status = 'ACTIVE' AND v.endTime > :now) " +
           "     OR (:status = 'PAUSED' AND v.status = 'PAUSED') " +
           "     OR (:status = 'EXPIRED' AND v.endTime <= :now) " +
           "     OR (v.status = :status))")
    Page<Voucher> findSellerVouchers(@Param("storeId") Long storeId,
                                    @Param("status") String status,
                                    @Param("now") Instant now,
                                    Pageable pageable);

    @Query("SELECT v FROM Voucher v WHERE " +
           "(:scope IS NULL OR :scope = 'ALL' " +
           "     OR (:scope = 'PLATFORM' AND v.store IS NULL) " +
           "     OR (:scope = 'STORE' AND v.store IS NOT NULL)) " +
           "AND (:status IS NULL OR :status = 'ALL' " +
           "     OR (:status = 'ACTIVE' AND v.status = 'ACTIVE' AND v.endTime > :now) " +
           "     OR (:status = 'PAUSED' AND v.status = 'PAUSED') " +
           "     OR (:status = 'EXPIRED' AND v.endTime <= :now) " +
           "     OR (v.status = :status)) " +
           "AND (:search IS NULL OR :search = '' OR LOWER(v.code) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Voucher> findAdminVouchers(@Param("scope") String scope,
                                   @Param("status") String status,
                                   @Param("search") String search,
                                   @Param("now") Instant now,
                                   Pageable pageable);

    @Query("SELECT COUNT(v) FROM Voucher v WHERE v.status = 'ACTIVE' AND v.endTime > :now")
    long countActiveVouchers(@Param("now") Instant now);

    @Query("SELECT COALESCE(SUM(CASE WHEN v.discountType = 'PERCENT' THEN COALESCE(v.maxDiscountAmount, v.discountValue * 1000) * v.totalQuantity ELSE v.discountValue * v.totalQuantity END), 0) FROM Voucher v")
    BigDecimal sumTotalDistributedBudget();

    @Query("SELECT COALESCE(SUM(v.usedQuantity), 0) FROM Voucher v")
    long sumTotalUsages();
}
