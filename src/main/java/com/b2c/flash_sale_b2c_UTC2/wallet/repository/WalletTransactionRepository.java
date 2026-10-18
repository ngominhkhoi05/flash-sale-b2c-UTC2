package com.b2c.flash_sale_b2c_UTC2.wallet.repository;

import com.b2c.flash_sale_b2c_UTC2.wallet.entity.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId);

    Page<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId, Pageable pageable);

    Page<WalletTransaction> findByWalletIdAndTransactionTypeOrderByCreatedAtDesc(Long walletId, String transactionType, Pageable pageable);

    @Query("SELECT COALESCE(SUM(ABS(wt.amount)), 0) FROM WalletTransaction wt WHERE wt.wallet.id = :walletId AND wt.transactionType = 'WITHDRAWAL'")
    BigDecimal sumWithdrawnByWalletId(@Param("walletId") Long walletId);
}
