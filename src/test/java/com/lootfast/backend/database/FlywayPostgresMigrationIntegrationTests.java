package com.lootfast.backend.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class FlywayPostgresMigrationIntegrationTests {

    private static final String DATABASE = "lootfast_test";
    private static final String USERNAME = "lootfast";
    private static final String PASSWORD = "test-password";

    @Container
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>(
            DockerImageName.parse("postgres:16-alpine")
    )
            .withEnv("POSTGRES_DB", DATABASE)
            .withEnv("POSTGRES_USER", USERNAME)
            .withEnv("POSTGRES_PASSWORD", PASSWORD)
            .withExposedPorts(5432)
            .waitingFor(Wait.forListeningPort());

    @Test
    void allFlywayMigrationsApplyToPostgresFromAnEmptyDatabase() throws Exception {
        String jdbcUrl = "jdbc:postgresql://%s:%d/%s".formatted(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(5432),
                DATABASE
        );
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, USERNAME, PASSWORD)
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();

        assertEquals(11, result.migrationsExecuted);
        try (var connection = DriverManager.getConnection(jdbcUrl, USERNAME, PASSWORD);
             var statement = connection.prepareStatement("""
                     select count(*)
                     from information_schema.tables
                     where table_schema = 'public'
                       and table_name in (
                           'external_auth_identities',
                           'user_consents',
                           'deals'
                       )
                     """)) {
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(3, rows.getInt(1));
            }
        }
    }
}
