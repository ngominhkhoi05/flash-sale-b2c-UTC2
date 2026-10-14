package com.b2c.flash_sale_b2c_UTC2.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình Cloudinary SDK (lưu trữ ảnh đa đối tượng - Image Module).
 * Giá trị đọc từ prefix "cloudinary" trong application.yaml.
 * Secret bắt buộc phải được cấu hình qua biến môi trường (AGENTS.md mục 29).
 */
@Configuration
@ConfigurationProperties(prefix = "cloudinary")
@Getter
@Setter
public class CloudinaryProperties {

    private String cloudName;
    private String apiKey;
    private String apiSecret;
    private String folder = "flash-sale-b2c/local";

    @PostConstruct
    public void validate() {
        boolean anyMissing = isBlank(cloudName) || isBlank(apiKey) || isBlank(apiSecret);
        // Cho phép app boot khi thiếu config để dev có thể start app trước khi có Cloudinary account;
        // CloudinaryService sẽ kiểm tra tại thời điểm upload và báo lỗi CLOUDINARY_FAILED rõ ràng.
        if (anyMissing) {
            // Không throw để tránh chặn toàn bộ app; service sẽ fail-fast khi cần gọi API.
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}