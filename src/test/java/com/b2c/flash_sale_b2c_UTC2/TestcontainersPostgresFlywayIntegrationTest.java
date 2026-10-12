package com.b2c.flash_sale_b2c_UTC2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test Flyway + ddl-auto: validate tren PostgreSQL container that.
 * Tu dong bo qua khi Docker Desktop daemon khong kha dung.
 *
 * Hien tai du an dung Supabase Dev DB qua application-test.yaml cho hau het test.
 * Test nay chi chay khi muon verify schema tren container ephemeral (vd CI/CD).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class TestcontainersPostgresFlywayIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("test_db")
            .withUsername("test_user")
            .withPassword("test_pass");

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Autowired(required = false)
    private DataSource dataSource;

    @MockitoBean
    private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Test
    @EnabledIf("isDockerAvailable")
    void testFlywayAndHibernateSchemaValidationOnRealPostgres() throws Exception {
        assertNotNull(dataSource, "DataSource phai duoc khoi tao");
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.isValid(2), "Ket noi den PostgreSQL container phai hop le");
        }
    }
}
