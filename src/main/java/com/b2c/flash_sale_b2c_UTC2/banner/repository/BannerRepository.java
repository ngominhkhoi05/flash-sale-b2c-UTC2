package com.b2c.flash_sale_b2c_UTC2.banner.repository;

import com.b2c.flash_sale_b2c_UTC2.banner.entity.Banner;
import com.b2c.flash_sale_b2c_UTC2.banner.enums.BannerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface BannerRepository extends JpaRepository<Banner, Long> {

    @Query(value = "SELECT b FROM Banner b WHERE b.isActive = true " +
                   "AND (b.startDate IS NULL OR b.startDate <= :now) " +
                   "AND (b.endDate IS NULL OR b.endDate >= :now) " +
                   "ORDER BY b.type ASC, b.position ASC")
    List<Banner> findActiveBanners(@Param("now") Instant now);

    @Query(value = "SELECT b FROM Banner b WHERE b.type = :type " +
                   "AND b.isActive = true " +
                   "AND (b.startDate IS NULL OR b.startDate <= :now) " +
                   "AND (b.endDate IS NULL OR b.endDate >= :now) " +
                   "ORDER BY b.position ASC")
    List<Banner> findActiveBannersByType(@Param("type") BannerType type, @Param("now") Instant now);

    @Query(value = "SELECT b FROM Banner b WHERE b.position = :position " +
                   "AND b.isActive = true " +
                   "AND (b.startDate IS NULL OR b.startDate <= :now) " +
                   "AND (b.endDate IS NULL OR b.endDate >= :now) " +
                   "ORDER BY b.position ASC")
    List<Banner> findActiveBannersByPosition(@Param("position") Integer position, @Param("now") Instant now);

    @Query(value = "SELECT b FROM Banner b WHERE b.isActive = true " +
                   "AND (b.startDate IS NULL OR b.startDate <= :now) " +
                   "AND (b.endDate IS NULL OR b.endDate >= :now) " +
                   "AND b.position = :position " +
                   "ORDER BY b.position ASC")
    List<Banner> findActiveByPosition(@Param("position") Integer position, @Param("now") Instant now);
}