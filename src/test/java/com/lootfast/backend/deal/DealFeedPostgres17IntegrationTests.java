package com.lootfast.backend.deal;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Reuses the data-bearing feed/filter/detail suite against the local Docker Compose major.
 * H2 does not reproduce PostgreSQL's typing of nullable LIKE parameters.
 * The container is disposable and never attaches an existing database volume.
 */
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DealFeedPostgres17IntegrationTests extends DealFeedIntegrationTests {

    @Container
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>(
            DockerImageName.parse("postgres:17-alpine")
    )
            .withEnv("POSTGRES_DB", "lootfast_test")
            .withEnv("POSTGRES_USER", "lootfast")
            .withEnv("POSTGRES_PASSWORD", "test-password")
            .withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://%s:%d/lootfast_test"
                .formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432)));
        registry.add("spring.datasource.username", () -> "lootfast");
        registry.add("spring.datasource.password", () -> "test-password");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
