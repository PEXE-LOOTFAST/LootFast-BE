package com.lootfast.backend.merchant.performance;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MerchantPerformancePostgres18Tests extends MerchantPerformanceTests {
  @Container static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:18.6-alpine")
      .withEnv("POSTGRES_DB", "report_test").withEnv("POSTGRES_USER", "lootfast")
      .withEnv("POSTGRES_PASSWORD", "test-password").withExposedPorts(5432)
      .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\n", 2));
  @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> "jdbc:postgresql://%s:%d/report_test"
        .formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432)));
    registry.add("spring.datasource.username", () -> "lootfast");
    registry.add("spring.datasource.password", () -> "test-password");
  }
}
