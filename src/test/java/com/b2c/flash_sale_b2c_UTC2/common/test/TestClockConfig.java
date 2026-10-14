package com.b2c.flash_sale_b2c_UTC2.common.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@TestConfiguration
public class TestClockConfig {

    private static Instant fixedNow = Instant.parse("2026-01-15T10:00:00Z");

    public static void setNow(Instant now) {
        fixedNow = now;
    }

    public static void resetNow() {
        fixedNow = Instant.parse("2026-01-15T10:00:00Z");
    }

    @Bean
    @Primary
    public Clock testClock() {
        return Clock.fixed(fixedNow, ZoneOffset.UTC);
    }
}