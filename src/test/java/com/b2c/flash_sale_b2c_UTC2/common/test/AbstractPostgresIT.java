package com.b2c.flash_sale_b2c_UTC2.common.test;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Base class for Integration Tests that use a real PostgreSQL (Supabase Dev DB).
 *
 * Why a real Postgres instead of Testcontainers:
 * - Docker is not available in the current development environment.
 * - Using Supabase directly (via application-test.yaml) allows testing real business
 *   logic with Flyway migration applied.
 *
 * Redis beans are mocked via @MockitoBean so services can inject without a real Redis.
 * TestCorsConfig provides a CorsConfigurationSource bean for SecurityConfig.
 * DbCleaner TRUNCATEs all business tables before EVERY test method.
 *
 * If a test needs real Redis, create a separate class - do NOT extend this one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestCorsConfig.class)
@ActiveProfiles({"test", "test-local"})
public abstract class AbstractPostgresIT {

    @MockitoBean
    private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private org.springframework.data.redis.core.RedisTemplate<Object, Object> redisTemplate;

    @Autowired
    protected DbCleaner dbCleaner;

    /**
     * Cleanup DB before each test method.
     * DbCleaner runs in its own transaction (REQUIRES_NEW) so TRUNCATE commits
     * even when the test method has its own @Transactional scope.
     */
    @BeforeEach
    protected void cleanDatabase() {
        dbCleaner.truncateAll();
    }
}
