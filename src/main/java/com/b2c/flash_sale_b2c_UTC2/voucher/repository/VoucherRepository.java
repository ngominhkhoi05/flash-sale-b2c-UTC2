package com.b2c.flash_sale_b2c_UTC2.voucher.repository;

import com.b2c.flash_sale_b2c_UTC2.voucher.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    Optional<Voucher> findByCode(String code);
    Optional<Voucher> findByCodeIgnoreCase(String code);
    boolean existsByCode(String code);
    List<Voucher> findByStoreId(Long storeId);
    List<Voucher> findByStoreIdIsNull();
}
