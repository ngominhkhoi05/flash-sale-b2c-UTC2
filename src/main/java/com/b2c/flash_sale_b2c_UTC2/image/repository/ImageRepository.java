package com.b2c.flash_sale_b2c_UTC2.image.repository;

import com.b2c.flash_sale_b2c_UTC2.image.entity.Image;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ImageRepository extends JpaRepository<Image, Long> {

    /** Lấy tất cả ảnh ACTIVE của một owner, sắp xếp theo thứ tự hiển thị. */
    List<Image> findByOwnerTypeAndOwnerIdAndStatusOrderByDisplayOrderAsc(
            ImageOwnerType ownerType, Long ownerId, String status);

    /** Lấy ảnh primary ACTIVE của một owner (áp dụng cho PRODUCT/VARIANT). */
    Optional<Image> findFirstByOwnerTypeAndOwnerIdAndIsPrimaryTrueAndStatus(
            ImageOwnerType ownerType, Long ownerId, String status);

    /** Lấy ảnh ACTIVE duy nhất của một owner 1-1 (USER/Store). */
    Optional<Image> findFirstByOwnerTypeAndOwnerIdAndStatus(
            ImageOwnerType ownerType, Long ownerId, String status);

    /**
     * Batch-load ảnh primary ACTIVE cho nhiều owner cùng lúc.
     * Tránh N+1 khi list nhiều Product/Variant.
     * Trả về List (mỗi Product tối đa 1 ảnh primary do partial unique index).
     */
    @Query("""
        SELECT i FROM Image i
        WHERE i.ownerType = :ownerType
          AND i.status = 'ACTIVE'
          AND i.isPrimary = true
          AND i.ownerId IN :ownerIds
        """)
    List<Image> findPrimaryImagesByOwnerIds(
            @Param("ownerType") ImageOwnerType ownerType,
            @Param("ownerIds") List<Long> ownerIds);

    /** Mark ảnh đã xóa trên Cloudinary (idempotency cho scheduled job + listener). */
    @Modifying
    @Query("UPDATE Image i SET i.cloudinaryDeleted = true, i.updatedAt = :now WHERE i.id = :id")
    int markCloudinaryDeleted(@Param("id") Long id, @Param("now") Instant now);

    /** Đánh dấu tất cả ảnh cũ cùng owner không còn primary. */
    @Modifying
    @Query("""
        UPDATE Image i SET i.isPrimary = false, i.updatedAt = :now
        WHERE i.ownerType = :ownerType AND i.ownerId = :ownerId AND i.id <> :keepId AND i.isPrimary = true
        """)
    int clearOtherPrimary(
            @Param("ownerType") ImageOwnerType ownerType,
            @Param("ownerId") Long ownerId,
            @Param("keepId") Long keepId,
            @Param("now") Instant now);

    /** Tìm các ảnh INACTIVE chưa xóa Cloudinary (retry cho scheduled job). */
    @Query("""
        SELECT i FROM Image i
        WHERE i.status = 'INACTIVE' AND i.cloudinaryDeleted = false
        """)
    List<Image> findInactiveNotCleanedUp();

    /** Tìm các ảnh INACTIVE đã xóa Cloudinary đủ 30 ngày để hard delete. */
    @Query("""
        SELECT i FROM Image i
        WHERE i.status = 'INACTIVE'
          AND i.cloudinaryDeleted = true
          AND i.updatedAt < :threshold
        """)
    List<Image> findReadyForHardDelete(@Param("threshold") Instant threshold);

    /**
     * Batch-load tất cả ảnh ACTIVE cho nhiều owner cùng lúc (không chỉ primary).
     * Dùng cho review album — lấy tất cả ảnh của các review để map vào response.
     */
    @Query("""
        SELECT i FROM Image i
        WHERE i.ownerType = :ownerType
          AND i.status = 'ACTIVE'
          AND i.ownerId IN :ownerIds
        ORDER BY i.displayOrder ASC
        """)
    List<Image> findByOwnerTypeAndOwnerIdIn(
            @Param("ownerType") ImageOwnerType ownerType,
            @Param("ownerIds") List<Long> ownerIds);
}