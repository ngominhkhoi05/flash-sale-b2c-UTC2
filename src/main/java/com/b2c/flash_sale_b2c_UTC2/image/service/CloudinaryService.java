package com.b2c.flash_sale_b2c_UTC2.image.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Giao tiếp với Cloudinary SDK. Tách interface để dễ mock trong test
 * và có thể thay provider lưu trữ (vd: AWS S3) nếu cần (theo docs mục 5.1).
 */
public interface CloudinaryService {

    /**
     * Upload ảnh lên Cloudinary.
     * @param file MultipartFile từ client
     * @param folder folder lưu trữ (vd: "avatars", "logos", "products/42")
     * @return kết quả gồm URL công khai và public_id (để xóa sau)
     */
    CloudinaryUploadResult upload(MultipartFile file, String folder);

    /**
     * Xóa ảnh trên Cloudinary bằng public_id.
     * Idempotent: gọi 2 lần vẫn an toàn (SDK trả về result "ok" string "not found").
     */
    void destroy(String publicId);

    /** DTO nhẹ trả về từ upload(). */
    record CloudinaryUploadResult(String url, String publicId) {}
}