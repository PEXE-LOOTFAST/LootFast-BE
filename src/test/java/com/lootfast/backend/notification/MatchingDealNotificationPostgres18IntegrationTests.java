package com.lootfast.backend.notification;

import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.notification.repository.UserNotificationRepository;
import com.lootfast.backend.notification.service.MatchingDealNotificationService;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class MatchingDealNotificationPostgres18IntegrationTests {

    @Container
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>(
            DockerImageName.parse("postgres:18.6-alpine")
    )
            .withEnv("POSTGRES_DB", "lootfast_notification_test")
            .withEnv("POSTGRES_USER", "lootfast")
            .withEnv("POSTGRES_PASSWORD", "test-password")
            .withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(
                    ".*database system is ready to accept connections.*\\n",
                    2
            ));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                "jdbc:postgresql://%s:%d/lootfast_notification_test".formatted(
                        POSTGRES.getHost(),
                        POSTGRES.getMappedPort(5432)
                ));
        registry.add("spring.datasource.username", () -> "lootfast");
        registry.add("spring.datasource.password", () -> "test-password");
    }

    @Autowired private UserRepository userRepository;
    @Autowired private MerchantProfileRepository merchantRepository;
    @Autowired private DealRepository dealRepository;
    @Autowired private MatchingDealNotificationService matchingService;
    @Autowired private UserNotificationRepository notificationRepository;

    @Test
    void nativeRecipientMatchingCreatesNotificationOnPostgres18() {
        User merchantUser = userRepository.saveAndFlush(user(
                "pg-merchant@example.com",
                "530000001",
                UserRole.MERCHANT
        ));
        User buyer = userRepository.saveAndFlush(user(
                "pg-buyer@example.com",
                "530000002",
                UserRole.BUYER
        ));
        MerchantProfile store = new MerchantProfile(merchantUser);
        store.updateStoreInformation("PG Store", null, "MEAL", null, null);
        store.updateLocation(
                "3 Test Street",
                null,
                "District 5",
                "Ho Chi Minh City",
                BigDecimal.valueOf(10.75),
                BigDecimal.valueOf(106.67)
        );
        store = merchantRepository.saveAndFlush(store);
        Deal deal = dealRepository.saveAndFlush(new Deal(
                store,
                "Postgres meal",
                "MEAL",
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(40_000),
                5,
                "https://cdn.lootfast.test/postgres-meal.png",
                10,
                Instant.parse("2030-01-02T09:00:00Z"),
                Instant.parse("2030-01-02T10:00:00Z"),
                Instant.parse("2030-01-02T01:00:00Z"),
                Instant.parse("2030-01-02T08:00:00Z"),
                DealStatus.ACTIVE
        ));

        matchingService.createForPublishedDeal(deal);
        notificationRepository.flush();

        assertEquals(1, notificationRepository.count());
        assertEquals(
                buyer.getId(),
                notificationRepository.findAll().getFirst().getRecipient().getId()
        );
    }

    private User user(String email, String phoneNumber, UserRole role) {
        return new User(
                "Postgres",
                "Tester",
                email,
                phoneNumber,
                "encoded-password",
                role,
                UserStatus.VERIFIED
        );
    }
}
