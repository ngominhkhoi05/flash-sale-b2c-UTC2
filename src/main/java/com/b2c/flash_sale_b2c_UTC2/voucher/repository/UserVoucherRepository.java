package com.b2c.flash_sale_b2c_UTC2.voucher.repository;

import com.b2c.flash_sale_b2c_UTC2.voucher.entity.UserVoucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserVoucherRepository extends JpaRepository<UserVoucher, Long> {

    boolean existsByUserIdAndVoucherId(Long userId, Long voucherId);

    Optional<UserVoucher> findByUserIdAndVoucherId(Long userId, Long voucherId);

    @Query("SELECT uv FROM UserVoucher uv JOIN FETCH uv.voucher v LEFT JOIN FETCH v.store WHERE uv.user.id = :userId ORDER BY uv.savedAt DESC")
    List<UserVoucher> findAllByUserIdWithVoucherAndStore(@Param("userId") Long userId);
}
