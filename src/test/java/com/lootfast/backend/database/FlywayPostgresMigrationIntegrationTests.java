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
import java.util.UUID;

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
    void latestMigrationUpgradesADataBearingV10Database() throws Exception {
        String jdbcUrl = "jdbc:postgresql://%s:%d/%s".formatted(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(5432),
                DATABASE
        );

        MigrateResult initialMigration = Flyway.configure()
                .dataSource(jdbcUrl, USERNAME, PASSWORD)
                .locations("classpath:db/migration")
                .target("10")
                .load()
                .migrate();
        assertEquals(10, initialMigration.migrationsExecuted);

        insertExistingMerchant(jdbcUrl);

        MigrateResult result = Flyway.configure()
                .dataSource(jdbcUrl, USERNAME, PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertEquals(1, result.migrationsExecuted);
        try (var connection = DriverManager.getConnection(jdbcUrl, USERNAME, PASSWORD);
             var statement = connection.prepareStatement("""
                     select
                         (
                             select count(*)
                             from information_schema.tables
                             where table_schema = 'public'
                               and table_name in (
                                   'external_auth_identities',
                                   'user_consents',
                                   'deals'
                               )
                         ) as feature_table_count,
                         (
                             select count(*)
                             from users
                             where email = 'merchant@example.com'
                               and account_type = 'MERCHANT'
                         ) as migrated_merchant_count
                     """)) {
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(3, rows.getInt(1));
                assertEquals(1, rows.getInt(2));
            }
        }
    }

    private void insertExistingMerchant(String jdbcUrl) throws Exception {
        long userId;
        try (var connection = DriverManager.getConnection(jdbcUrl, USERNAME, PASSWORD);
             var statement = connection.prepareStatement("""
                     insert into users (
                         public_id,
                         email,
                         password_hash,
                         first_name,
                         last_name,
                         phone_number,
                         role,
                         status,
                         created_at
                     ) values (?, ?, ?, ?, ?, ?, ?, ?, now())
                     returning id
                     """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, "merchant@example.com");
            statement.setString(3, "encoded-password");
            statement.setString(4, "Minh");
            statement.setString(5, "Tran");
            statement.setString(6, "123456789");
            statement.setString(7, "BUYER");
            statement.setString(8, "VERIFIED");
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                userId = rows.getLong(1);
            }
        }

        try (var connection = DriverManager.getConnection(jdbcUrl, USERNAME, PASSWORD);
             var statement = connection.prepareStatement("""
                     insert into user_roles (user_id, role)
                     values (?, 'MERCHANT')
                     """)) {
            statement.setLong(1, userId);
            assertEquals(1, statement.executeUpdate());
        }
    }
}
