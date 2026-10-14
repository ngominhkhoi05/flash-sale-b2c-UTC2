package com.b2c.flash_sale_b2c_UTC2.image.listener;

import com.b2c.flash_sale_b2c_UTC2.image.event.CloudinaryCleanupEvent;
import com.b2c.flash_sale_b2c_UTC2.image.repository.ImageRepository;
import com.b2c.flash_sale_b2c_UTC2.image.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * Lắng nghe CloudinaryCleanupEvent ở phase AFTER_COMMIT (sau khi DB transaction commit).
 * Gọi Cloudinary destroy rồi đánh dấu cloudinary_deleted=true.
 * Nếu Cloudinary fail, không throw để tránh ảnh hưởng flow khác;
 * Scheduled job sẽ retry cho các record INACTIVE+cloudinary_deleted=false.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CloudinaryCleanupListener {

    private final CloudinaryService cloudinaryService;
    private final ImageRepository imageRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCleanup(CloudinaryCleanupEvent event) {
        if (event == null || event.publicId() == null || event.publicId().isBlank()) {
            return;
        }
        try {
            cloudinaryService.destroy(event.publicId());
            imageRepository.markCloudinaryDeleted(event.imageId(), Instant.now());
            log.info("Đã dọn ảnh trên Cloudinary: imageId={}, publicId={}", event.imageId(), event.publicId());
        } catch (Exception ex) {
            log.warn("Cloudinary destroy thất bại (sẽ retry bởi scheduled job): imageId={}, publicId={}, error={}",
                    event.imageId(), event.publicId(), ex.getMessage());
        }
    }
}