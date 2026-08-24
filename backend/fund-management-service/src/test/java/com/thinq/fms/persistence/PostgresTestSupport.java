package com.thinq.fms.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

/**
 * A real PostgreSQL, migrated by Flyway, shared across every test that extends this.
 *
 * <p>The rules this system enforces in schema rather than in code cannot be tested any other way. A
 * stub repository has no constraints to violate, so a test against one passes whether the index
 * exists or not — which is exactly how a partial unique index with a predicate that misses a state
 * reaches production looking reviewed.
 *
 * <p>The container is started once for the JVM and never stopped. Testcontainers' Ryuk sidecar
 * removes it when the JVM exits, and starting one container per test class would add roughly a
 * second each for no isolation benefit, because every test here creates its own account
 * identifiers.
 */
public abstract class PostgresTestSupport {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    protected static JdbcClient db;

    @BeforeAll
    static void startAndMigrate() {
        if (dataSource != null) {
            return;
        }
        POSTGRES.start();

        DriverManagerDataSource source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        source.setDriverClassName("org.postgresql.Driver");
        dataSource = source;

        // The same migrations the service ships, applied the same way. Running the schema from a
        // hand-written fixture instead would test a schema nothing deploys.
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();

        db = JdbcClient.create(dataSource);
    }

    /** The container's connection details, for tests that configure a DataSource by property. */
    protected static String jdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    protected static String username() {
        return POSTGRES.getUsername();
    }

    protected static String password() {
        return POSTGRES.getPassword();
    }

    protected static DataSource dataSource() {
        return dataSource;
    }
}
