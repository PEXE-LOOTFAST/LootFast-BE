package com.lootfast.backend.auth.social;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SocialPendingAccountPostgres18Tests extends SocialPendingAccountIntegrationTests {
    @Container static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:18.6-alpine")
            .withEnv("POSTGRES_DB", "social_test").withEnv("POSTGRES_USER", "lootfast")
            .withEnv("POSTGRES_PASSWORD", "test-password").withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\n", 2));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:postgresql://%s:%d/social_test"
                .formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432)));
        r.add("spring.datasource.username", () -> "lootfast");
        r.add("spring.datasource.password", () -> "test-password");
        r.add("spring.flyway.enabled", () -> true);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
