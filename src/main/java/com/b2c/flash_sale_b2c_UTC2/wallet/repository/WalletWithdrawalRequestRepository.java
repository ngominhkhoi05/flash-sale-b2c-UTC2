package com.b2c.flash_sale_b2c_UTC2.wallet.repository;

import com.b2c.flash_sale_b2c_UTC2.wallet.entity.WalletWithdrawalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletWithdrawalRequestRepository extends JpaRepository<WalletWithdrawalRequest, Long> {

    Optional<WalletWithdrawalRequest> findByRequestCode(String requestCode);

    Optional<WalletWithdrawalRequest> findByIdempotencyKey(String idempotencyKey);

    Page<WalletWithdrawalRequest> findByStoreId(Long storeId, Pageable pageable);

    @Query("SELECT r FROM WalletWithdrawalRequest r WHERE " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:storeId IS NULL OR r.store.id = :storeId)")
    Page<WalletWithdrawalRequest> findWithFilters(
            @Param("status") String status,
            @Param("storeId") Long storeId,
            Pageable pageable
    );
}
