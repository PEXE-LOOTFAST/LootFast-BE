package com.lootfast.backend.review;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class StoreReviewPostgres17Tests extends StoreReviewTests {
  @Container
  static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:17-alpine")
      .withEnv("POSTGRES_DB", "review_test").withEnv("POSTGRES_USER", "lootfast")
      .withEnv("POSTGRES_PASSWORD", "test-password").withExposedPorts(5432)
      .waitingFor(Wait.forSuccessfulCommand("PGPASSWORD=test-password PGCONNECT_TIMEOUT=2 psql -h 127.0.0.1 -U lootfast -d review_test --no-password -v ON_ERROR_STOP=1 -tAc 'SELECT 1'"));

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> "jdbc:postgresql://%s:%d/review_test".formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432)));
    registry.add("spring.datasource.username", () -> "lootfast");
    registry.add("spring.datasource.password", () -> "test-password");
  }
}
