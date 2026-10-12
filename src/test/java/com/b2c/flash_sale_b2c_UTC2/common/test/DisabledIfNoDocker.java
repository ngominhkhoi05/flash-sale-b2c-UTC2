package com.b2c.flash_sale_b2c_UTC2.common.test;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that skips a test when Docker Desktop daemon is not available.
 * Use for integration tests that require Redis container or Postgres container.
 *
 * Example:
 * <pre>{@code
 * @SpringBootTest
 * @DisabledIfNoDocker
 * class MyRedisIntegrationTest { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ExtendWith(DisabledIfNoDockerCondition.class)
public @interface DisabledIfNoDocker {
}
