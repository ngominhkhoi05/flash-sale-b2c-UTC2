package com.b2c.flash_sale_b2c_UTC2.image.event;

/**
 * Sự kiện phát ra khi một ảnh đã được soft-delete (status=INACTIVE) trong DB.
 * Listener sẽ nhận sự kiện này ở phase AFTER_COMMIT để gọi Cloudinary destroy
 * mà không sợ rollback DB (AGENTS.md mục 25).
 */
public record CloudinaryCleanupEvent(Long imageId, String publicId) {
}