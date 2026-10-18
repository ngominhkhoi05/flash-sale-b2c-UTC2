package com.b2c.flash_sale_b2c_UTC2.wallet.repository;

import com.b2c.flash_sale_b2c_UTC2.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByStoreId(Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.store.id = :storeId")
    Optional<Wallet> findByStoreIdWithLock(@Param("storeId") Long storeId);
}
