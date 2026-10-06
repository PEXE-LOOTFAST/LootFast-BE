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
            DockerImageName.parse("postgres:18.6")
    )
            .withEnv("POSTGRES_DB", DATABASE)
            .withEnv("POSTGRES_USER", USERNAME)
            .withEnv("POSTGRES_PASSWORD", PASSWORD)
            .withExposedPorts(5432)
            .waitingFor(Wait.forListeningPort());

    @Test
    void latestMigrationsUpgradeADataBearingV10Database() throws Exception {
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

        ExistingMerchant existingMerchant = insertExistingMerchant(jdbcUrl);

        MigrateResult dealMigration = Flyway.configure()
                .dataSource(jdbcUrl, USERNAME, PASSWORD)
                .locations("classpath:db/migration")
                .target("11")
                .load()
                .migrate();
        assertEquals(1, dealMigration.migrationsExecuted);
        insertExistingDeal(jdbcUrl, existingMerchant.profileId());

        MigrateResult result = Flyway.configure()
                .dataSource(jdbcUrl, USERNAME, PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        // V12-V32, including merchant bank accounts, order records, MoMo removal,
        // merchant deal management, the order finalized_at column, and the order
        // price/pickup snapshots, deal lifecycle, payment audit, verification badge,
        // notification preferences, matching-deal notifications, store reports,
        // the merchant review flag, merchant sanctions and support tickets, apply on V11.
        assertEquals(21, result.migrationsExecuted);
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
                                   'deals',
                                   'user_favorites',
                                   'deal_templates',
                                   'order_payment_events',
                                   'notification_preferences',
                                   'notifications'
                               )
                         ) as feature_table_count,
                         (
                             select count(*)
                             from user_roles roles
                             join users on users.id = roles.user_id
                             where users.email = 'merchant@example.com'
                               and roles.role in ('BUYER', 'MERCHANT')
                         ) as retained_role_count,
                         (
                             select count(*)
                             from information_schema.columns
                             where table_schema = 'public'
                               and table_name = 'users'
                               and column_name = 'account_type'
                         ) as account_type_column_count,
                         (
                             select search_name
                             from deals
                             where name = 'Bánh mì đặc biệt'
                         ) as normalized_deal_name,
                         (
                             select search_name
                             from merchant_profiles
                             where id = ?
                         ) as normalized_store_name,
                         (
                             select total_quantity
                             from deals
                             where name = 'Bánh mì đặc biệt'
                         ) as migrated_total_quantity,
                         (
                             select count(*)
                             from deal_images images
                             join deals on deals.id = images.deal_id
                             where deals.name = 'Bánh mì đặc biệt'
                               and images.status = 'ACTIVE'
                         ) as migrated_image_count,
                         (
                             select count(*)
                             from information_schema.columns
                             where table_schema = 'public'
                               and table_name = 'customer_orders'
                               and column_name = 'pickup_code'
                               and is_nullable = 'NO'
                         ) as pickup_code_column_count,
                         (
                             select count(*)
                             from pg_indexes
                             where schemaname = 'public'
                               and tablename = 'customer_orders'
                               and indexname = 'idx_orders_merchant_pickup_start'
                         ) as merchant_pickup_index_count,
                         (
                             select count(*)
                             from information_schema.columns
                             where table_schema = 'public'
                               and table_name = 'deals'
                               and column_name in (
                                   'hidden_at',
                                   'removed_at',
                                   'removed_by_user_id',
                                   'removal_reason'
                               )
                         ) as deal_lifecycle_column_count,
                         (
                             select count(*)
                             from information_schema.columns
                             where table_schema = 'public'
                               and table_name = 'customer_orders'
                               and column_name in (
                                   'payment_reported_at',
                                   'payment_confirmed_at',
                                   'payment_confirmed_by_user_id'
                               )
                         ) as payment_audit_column_count,
                         (
                             select count(*)
                             from merchant_profiles
                             where id = ?
                               and verified_at is not null
                         ) as verified_profile_count
                     """)) {
            statement.setLong(1, existingMerchant.profileId());
            statement.setLong(2, existingMerchant.profileId());
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(8, rows.getInt(1));
                assertEquals(2, rows.getInt(2));
                assertEquals(0, rows.getInt(3));
                assertEquals("banh mi dac biet", rows.getString(4));
                assertEquals("bep viet", rows.getString(5));
                assertEquals(3, rows.getInt(6));
                assertEquals(1, rows.getInt(7));
                assertEquals(1, rows.getInt(8));
                assertEquals(1, rows.getInt(9));
                assertEquals(4, rows.getInt(10));
                assertEquals(3, rows.getInt(11));
                assertEquals(1, rows.getInt(12));
            }
        }
    }

    private ExistingMerchant insertExistingMerchant(String jdbcUrl) throws Exception {
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

        long profileId;
        try (var connection = DriverManager.getConnection(jdbcUrl, USERNAME, PASSWORD);
             var statement = connection.prepareStatement("""
                     insert into merchant_profiles (
                         user_id,
                         store_name,
                         latitude,
                         longitude,
                         created_at,
                         updated_at
                     ) values (?, 'Bếp Việt', 10.776900, 106.700900, now(), now())
                     returning id
                     """)) {
            statement.setLong(1, userId);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                profileId = rows.getLong(1);
            }
        }

        try (var connection = DriverManager.getConnection(jdbcUrl, USERNAME, PASSWORD);
             var statement = connection.prepareStatement("""
                     insert into merchant_applications (
                         public_id,
                         user_id,
                         business_license_number,
                         license_storage_key,
                         license_format,
                         submission_id,
                         status,
                         submitted_at,
                         reviewed_at,
                         created_at,
                         updated_at
                     ) values (?, ?, '03123456789012', 'license-key', 'png', ?,
                               'APPROVED', now(), now(), now(), now())
                     """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setLong(2, userId);
            statement.setObject(3, UUID.randomUUID());
            assertEquals(1, statement.executeUpdate());
        }
        return new ExistingMerchant(userId, profileId);
    }

    private void insertExistingDeal(String jdbcUrl, long profileId) throws Exception {
        try (var connection = DriverManager.getConnection(jdbcUrl, USERNAME, PASSWORD);
             var statement = connection.prepareStatement("""
                     insert into deals (
                         public_id,
                         merchant_profile_id,
                         name,
                         category_code,
                         original_price,
                         discounted_price,
                         remaining_quantity,
                         primary_image_url,
                         preparation_minutes,
                         pickup_start,
                         pickup_end,
                         available_from,
                         available_until,
                         status,
                         version,
                         created_at,
                         updated_at
                     ) values (
                         ?, ?, 'Bánh mì đặc biệt', 'BAKERY', 100000, 50000, 3,
                         'https://cdn.lootfast.test/deal.jpg', 10,
                         now(), now() + interval '2 hours',
                         now() - interval '1 hour', now() + interval '2 hours',
                         'ACTIVE', 0, now(), now()
                     )
                     """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setLong(2, profileId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private record ExistingMerchant(long userId, long profileId) {
    }
}
