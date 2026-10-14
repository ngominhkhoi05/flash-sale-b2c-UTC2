package com.b2c.flash_sale_b2c_UTC2.image.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.image.exception.ImageErrorCode;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Triển khai CloudinaryService dùng Cloudinary SDK.
 * Validate file (kích thước tối đa 5MB, định dạng HTTP/PNG/WebP) trước khi gọi API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements CloudinaryService {

    /** 5 MB theo docs mục 28.5. */
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png", "image/webp");

    private final Cloudinary cloudinary;

    @Override
    public CloudinaryUploadResult upload(MultipartFile file, String folder) {
        validate(file);

        try {
            // Upload có public_id do Cloudinary tạo ngẫu nhiên để tránh xung đột
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image",
                            "overwrite", false,
                            "unique_filename", true,
                            "use_filename", false
                    ));

            String url = (String) result.get("secure_url");
            String publicId = (String) result.get("public_id");
            log.info("Đã upload ảnh lên Cloudinary: folder={}, publicId={}", folder, publicId);
            return new CloudinaryUploadResult(url, publicId);
        } catch (IOException e) {
            log.error("Lỗi đọc multipart file khi upload lên Cloudinary", e);
            throw new BusinessException(ImageErrorCode.CLOUDINARY_FAILED, e);
        } catch (Exception e) {
            log.error("Cloudinary upload thất bại: {}", e.getMessage(), e);
            throw new BusinessException(ImageErrorCode.CLOUDINARY_FAILED, e);
        }
    }

    @Override
    public void destroy(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return; // idempotent
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("invalidate", true));
            String resultStatus = String.valueOf(result.get("result"));
            if (!"ok".equalsIgnoreCase(resultStatus) && !"not found".equalsIgnoreCase(resultStatus)) {
                log.warn("Cloudinary destroy trả về status lạ: publicId={}, result={}", publicId, result);
            } else {
                log.info("Đã xóa ảnh trên Cloudinary: publicId={}, status={}", publicId, resultStatus);
            }
        } catch (Exception e) {
            log.error("Cloudinary destroy thất bại: publicId={}, error={}", publicId, e.getMessage());
            throw new BusinessException(ImageErrorCode.CLOUDINARY_FAILED, e);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ImageErrorCode.FILE_EMPTY);
        }
        long size = file.getSize();
        if (size > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(ImageErrorCode.FILE_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(ImageErrorCode.INVALID_FORMAT);
        }
        // Double-check phần mở rộng để tránh MIME spoofing
        String original = file.getOriginalFilename();
        if (original != null) {
            String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
            if (!Arrays.asList("jpg", "jpeg", "png", "webp").contains(ext)) {
                throw new BusinessException(ImageErrorCode.INVALID_FORMAT);
            }
        }
    }
}