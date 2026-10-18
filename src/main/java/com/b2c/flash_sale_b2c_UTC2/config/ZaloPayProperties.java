package com.b2c.flash_sale_b2c_UTC2.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "zalopay")
@Getter
@Setter
public class ZaloPayProperties {

    private String appId;
    private String key1;
    private String key2;
    private String endpoint = "https://sb-openapi.zalopay.vn/v2/create";
    private String callbackUrl;
    private String appUser = "FlashSaleB2C";
}