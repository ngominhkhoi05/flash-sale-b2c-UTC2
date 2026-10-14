package com.b2c.flash_sale_b2c_UTC2.image.scheduler;

import com.b2c.flash_sale_b2c_UTC2.image.entity.Image;
import com.b2c.flash_sale_b2c_UTC2.image.repository.ImageRepository;
import com.b2c.flash_sale_b2c_UTC2.image.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Scheduled job dọn ảnh Image Module (theo docs mục 5.4 và 28.3).
 * Chạy 02:00 mỗi ngày. Đảm bảo idempotent (AGENTS.md mục 36, 37).
 *
 * <ol>
 *   <li>Retry Cloudinary destroy cho record INACTIVE + cloudinary_deleted=false (event listener fail trước đó).</li>
 *   <li>Hard-delete record INACTIVE + cloudinary_deleted=true + updated_at &lt; now()-30 ngày.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageCleanupScheduler {

    /** Lưu trữ 30 ngày sau khi đã xóa trên Cloudinary thành công. */
    private static final Duration RETENTION = Duration.ofDays(30);

    private final ImageRepository imageRepository;
    private final CloudinaryService cloudinaryService;

    @Scheduled(cron = "0 0 2 * * *") // 02:00 hàng ngày (giờ server)
    @Transactional
    public void cleanup() {
        int retried = retryFailedCloudinaryDestroys();
        int hardDeleted = hardDeleteOldInactiveImages();
        log.info("Image cleanup kết thúc: retried={}, hardDeleted={}", retried, hardDeleted);
    }

    private int retryFailedCloudinaryDestroys() {
        List<Image> pending = imageRepository.findInactiveNotCleanedUp();
        int retried = 0;
        for (Image img : pending) {
            if (img.getCloudinaryPublicId() == null || img.getCloudinaryPublicId().isBlank()) {
                // Record không có public_id (vd: lúc trước upload fail) → chỉ đánh dấu cloudinary_deleted=true
                imageRepository.markCloudinaryDeleted(img.getId(), Instant.now());
                retried++;
                continue;
            }
            try {
                cloudinaryService.destroy(img.getCloudinaryPublicId());
                imageRepository.markCloudinaryDeleted(img.getId(), Instant.now());
                retried++;
            } catch (Exception ex) {
                log.warn("Retry Cloudinary destroy vẫn thất bại: imageId={}, publicId={}, error={}",
                        img.getId(), img.getCloudinaryPublicId(), ex.getMessage());
            }
        }
        return retried;
    }

    private int hardDeleteOldInactiveImages() {
        Instant threshold = Instant.now().minus(RETENTION);
        List<Image> ready = imageRepository.findReadyForHardDelete(threshold);
        if (ready.isEmpty()) {
            return 0;
        }
        imageRepository.deleteAll(ready);
        log.info("Đã hard-delete {} ảnh INACTIVE > 30 ngày đã dọn Cloudinary", ready.size());
        return ready.size();
    }
}