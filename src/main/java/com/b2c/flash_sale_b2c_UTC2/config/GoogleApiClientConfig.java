package com.b2c.flash_sale_b2c_UTC2.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Bean Google API Client HttpTransport dung de verify Google id_token
 * signature (qua JWKS endpoint) trong GoogleOAuth2ServiceImpl.
 *
 * Bean duoc tao lan dau (singleton) va duoc Google API client cache JWKS
 * tu dong, nen khong gay them round-trip cho moi request.
 */
@Slf4j
@Configuration
public class GoogleApiClientConfig {

    @Bean
    public HttpTransport googleHttpTransport() {
        try {
            return GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException ex) {
            // Neu khong tao duoc HttpTransport (host khong co internet luc boot),
            // tra ve null va de GoogleOAuth2ServiceImpl fail-fast voi error code ro rang.
            log.error("Khong the khoi tao Google HttpTransport: {}", ex.getMessage());
            return null;
        }
    }
}