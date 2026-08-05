package io.casehub.ledger.test;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class PostgreSQLTestResource implements QuarkusTestResourceLifecycleManager {

    private static final Logger LOG = LoggerFactory.getLogger(PostgreSQLTestResource.class);
    private static final int MAX_ATTEMPTS = 3;

    private PostgreSQLContainer container;

    @Override
    public Map<String, String> start() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                container = new PostgreSQLContainer("postgres:17-alpine")
                        .withStartupTimeout(Duration.ofSeconds(120));
                container.start();
                return Map.of(
                        "quarkus.datasource.jdbc.url", container.getJdbcUrl(),
                        "quarkus.datasource.username", container.getUsername(),
                        "quarkus.datasource.password", container.getPassword());
            } catch (final Exception e) {
                LOG.warn("PostgreSQL container start attempt {}/{} failed: {}",
                        attempt, MAX_ATTEMPTS, e.getMessage());
                if (container != null) {
                    try { container.stop(); } catch (final Exception ignored) { }
                    container = null;
                }
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("unreachable");
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
