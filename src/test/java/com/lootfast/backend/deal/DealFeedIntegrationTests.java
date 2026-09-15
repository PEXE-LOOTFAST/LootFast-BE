package com.lootfast.backend.deal;

import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.deal.dto.DealFeedItemResponse;
import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.deal.service.DealFeedService;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({DealFeedService.class, DealFeedIntegrationTests.FixedClockConfig.class})
class DealFeedIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-09-15T04:00:00Z");

    @Autowired private UserRepository userRepository;
    @Autowired private MerchantProfileRepository merchantProfileRepository;
    @Autowired private DealRepository dealRepository;
    @Autowired private DealFeedService dealFeedService;

    @BeforeEach
    void setUp() {
        createDeal("Near bakery", 10.776900, 106.700900, DealStatus.ACTIVE, 3);
        createDeal("Far bakery", 10.783000, 106.707000, DealStatus.ACTIVE, 2);
        createDeal("Hidden bakery", 10.776950, 106.700950, DealStatus.HIDDEN, 4);
        createDeal("Sold out bakery", 10.777000, 106.701000, DealStatus.ACTIVE, 0);
    }

    @Test
    void feedReturnsOnlyActiveAvailableDealsWithinRadiusSortedByDistance() {
        PagedResponse<DealFeedItemResponse> response = dealFeedService.getFeed(
                10.776889,
                106.700806,
                1.0,
                0,
                20
        );

        assertEquals(2, response.totalElements());
        assertEquals("Near bakery", response.items().get(0).name());
        assertEquals("Far bakery", response.items().get(1).name());
        assertTrue(response.items().get(0).merchant().verified());
    }

    @Test
    void feedPaginatesAfterDistanceSorting() {
        PagedResponse<DealFeedItemResponse> response = dealFeedService.getFeed(
                10.776889,
                106.700806,
                1.0,
                1,
                1
        );

        assertEquals(2, response.totalElements());
        assertEquals(2, response.totalPages());
        assertEquals("Far bakery", response.items().getFirst().name());
    }

    private void createDeal(
            String name,
            double latitude,
            double longitude,
            DealStatus status,
            int remainingQuantity
    ) {
        User merchantUser = userRepository.save(new User(
                name.substring(0, Math.min(10, name.length())),
                "Store",
                name.toLowerCase().replace(' ', '.') + "@gmail.com",
                null,
                null,
                UserRole.MERCHANT,
                UserStatus.VERIFIED
        ));
        MerchantProfile profile = new MerchantProfile(merchantUser);
        profile.updateStoreInformation(
                name + " Store",
                "Description",
                "BAKERY",
                null,
                null
        );
        profile.updateLocation(
                "1 Test Street",
                "Ben Nghe",
                "District 1",
                "Ho Chi Minh City",
                BigDecimal.valueOf(latitude),
                BigDecimal.valueOf(longitude)
        );
        merchantProfileRepository.save(profile);
        dealRepository.save(new Deal(
                profile,
                name,
                "BAKERY",
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(50_000),
                remainingQuantity,
                "https://cdn.lootfast.test/deal.jpg",
                10,
                NOW.plusSeconds(3_600),
                NOW.plusSeconds(7_200),
                NOW.minusSeconds(3_600),
                NOW.plusSeconds(7_200),
                status
        ));
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
