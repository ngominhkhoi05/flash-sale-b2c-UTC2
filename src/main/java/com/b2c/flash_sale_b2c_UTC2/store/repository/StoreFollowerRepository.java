package com.b2c.flash_sale_b2c_UTC2.store.repository;

import com.b2c.flash_sale_b2c_UTC2.store.entity.StoreFollower;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoreFollowerRepository extends JpaRepository<StoreFollower, Long> {

    boolean existsByStoreIdAndUserId(@Param("storeId") Long storeId, @Param("userId") Long userId);

    @Query("SELECT COUNT(sf) FROM StoreFollower sf WHERE sf.store.id = :storeId")
    long countByStoreId(@Param("storeId") Long storeId);
}