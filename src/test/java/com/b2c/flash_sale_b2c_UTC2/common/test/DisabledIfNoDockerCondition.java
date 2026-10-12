package com.b2c.flash_sale_b2c_UTC2.common.test;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Condition that checks if Docker is available.
 * - Docker OK -> test runs normally (enabled).
 * - Docker unavailable -> test is disabled with a clear message.
 */
public class DisabledIfNoDockerCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        // Check Redis port 6379 first (most reliable indicator for our test env)
        boolean redisAvailable = isPortOpen("localhost", 6379);
        // Also try Docker as fallback
        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            dockerAvailable = false;
        }
        if (redisAvailable || dockerAvailable) {
            return ConditionEvaluationResult.enabled(
                    "Redis or Docker available (redis=" + redisAvailable + ", docker=" + dockerAvailable + ")");
        }
        return ConditionEvaluationResult.disabled(
                "Neither Redis (localhost:6379) nor Docker is available - test is skipped");
    }

    private boolean isPortOpen(String host, int port) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Static helper: check if Redis (localhost:6379) or Docker is available.
     * Used by JUnit @BeforeAll Assumptions to short-circuit before Spring context loads.
     */
    public static boolean isRedisOrDockerAvailable() {
        if (isPortOpenStatic("localhost", 6379)) {
            return true;
        }
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isPortOpenStatic(String host, int port) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
