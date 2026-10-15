package com.b2c.flash_sale_b2c_UTC2.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a Jackson 2.x {@link ObjectMapper} bean for business code.
 *
 * <p>Spring Boot 4.x auto-configures a Jackson 3.x
 * {@code tools.jackson.databind.ObjectMapper} by default. However, business
 * code (e.g. {@code PaymentServiceImpl}, {@code FlashSaleReservationService})
 * imports {@code com.fasterxml.jackson.databind.ObjectMapper} (Jackson 2.x) —
 * these are two different packages, so the Boot 4 default bean cannot satisfy
 * that injection point and the application context fails to start.</p>
 *
 * <p>This configuration registers a Jackson 2 {@code ObjectMapper} with the
 * minimum setup required to serialize {@code Instant} / {@code java.time}
 * values correctly (ISO-8601 string instead of numeric timestamp).</p>
 */
@Configuration
public class ObjectMapperConfig {

    @Bean
    @ConditionalOnMissingBean(name = "legacyJackson2ObjectMapper")
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
