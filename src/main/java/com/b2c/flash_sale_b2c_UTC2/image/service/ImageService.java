package com.b2c.flash_sale_b2c_UTC2.image.service;

import com.b2c.flash_sale_b2c_UTC2.image.dto.ImageResponse;
import com.b2c.flash_sale_b2c_UTC2.image.enums.ImageOwnerType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Business logic quản lý ảnh đa đối tượng (polymorphic).
 * Theo docs/04_architecture_analysis.md mục 5 và docs/api/api-document.md mục 28.
 */
public interface ImageService {

    /**
     * Upload ảnh mới và gắn vào owner.
     * @param currentUserId user hiện tại (phải sở hữu owner)
     * @param ownerType loại đối tượng
     * @param ownerId id của đối tượng
     * @param file file ảnh
     * @param isPrimary áp dụng cho PRODUCT/VARIANT, bỏ qua cho USER/STORE/REVIEW
     * @param displayOrder thứ tự hiển thị (mặc định 0)
     */
    ImageResponse attachImage(Long currentUserId, ImageOwnerType ownerType, Long ownerId,
                              MultipartFile file, Boolean isPrimary, Integer displayOrder);

    /** Lấy tất cả ảnh ACTIVE của owner, sort theo displayOrder. */
    List<ImageResponse> listImages(ImageOwnerType ownerType, Long ownerId);

    /** Lấy ảnh primary ACTIVE của owner (PRODUCT/VARIANT), null nếu không có. */
    ImageResponse getPrimaryImage(ImageOwnerType ownerType, Long ownerId);

    /**
     * Batch load ảnh primary cho nhiều owner cùng lúc (tránh N+1).
     * Trả về Map ownerId -> ImageResponse; ownerId không có ảnh primary sẽ không có key.
     */
    Map<Long, ImageResponse> getPrimaryImagesByOwnerIds(ImageOwnerType ownerType, List<Long> ownerIds);

    /** Soft delete một ảnh (status=INACTIVE) + phát event async cleanup Cloudinary. */
    void detachImage(Long currentUserId, Long imageId);

    /** Hard delete (Admin only) + đồng bộ xóa Cloudinary ngay. */
    void forceDeleteImage(Long imageId);

    /** Đánh dấu ảnh là primary (chỉ PRODUCT/VARIANT). */
    ImageResponse setPrimary(Long currentUserId, Long imageId);

    /** Cập nhật displayOrder (chỉ REVIEW gallery 1-N). */
    ImageResponse setDisplayOrder(Long currentUserId, Long imageId, int newOrder);
}