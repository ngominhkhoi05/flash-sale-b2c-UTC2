package com.b2c.flash_sale_b2c_UTC2.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Khởi tạo {@link Cloudinary} bean từ {@link CloudinaryProperties}.
 * Bean chỉ được tạo khi có đủ credential; nếu không, CloudinaryService sẽ fail tại runtime
 * với error code CLOUDINARY_FAILED (đã có guard trong service).
 */
@Configuration
public class CloudinaryConfig {

    @Bean
    public Cloudinary cloudinary(CloudinaryProperties props) {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", props.getCloudName(),
                "api_key", props.getApiKey(),
                "api_secret", props.getApiSecret(),
                "secure", true
        ));
    }
}