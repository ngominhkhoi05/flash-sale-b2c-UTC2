package com.b2c.flash_sale_b2c_UTC2.wallet.repository;

import com.b2c.flash_sale_b2c_UTC2.wallet.entity.StoreBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoreBankAccountRepository extends JpaRepository<StoreBankAccount, Long> {
    Optional<StoreBankAccount> findByStoreId(Long storeId);
}
