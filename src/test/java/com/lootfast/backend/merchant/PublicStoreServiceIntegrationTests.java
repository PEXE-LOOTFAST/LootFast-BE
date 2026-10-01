package com.lootfast.backend.merchant;

import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.deal.service.DealResponseMapper;
import com.lootfast.backend.merchant.dto.PublicStoreProfileResponse;
import com.lootfast.backend.merchant.entity.MerchantOpeningHour;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantOpeningHourRepository;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.merchant.service.PublicStoreService;
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
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({
        PublicStoreService.class,
        DealResponseMapper.class,
        PublicStoreServiceIntegrationTests.FixedClockConfig.class
})
class PublicStoreServiceIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-09-15T04:00:00Z");

    @Autowired private UserRepository userRepository;
    @Autowired private MerchantProfileRepository merchantProfileRepository;
    @Autowired private MerchantOpeningHourRepository openingHourRepository;
    @Autowired private DealRepository dealRepository;
    @Autowired private PublicStoreService publicStoreService;

    private User merchant;
    private MerchantProfile store;

    @BeforeEach
    void setUp() {
        merchant = userRepository.save(new User(
                "Minh",
                "Tran",
                "public-store@example.com",
                null,
                null,
                UserRole.MERCHANT,
                UserStatus.VERIFIED
        ));
        store = new MerchantProfile(merchant);
        store.updateStoreInformation(
                "Bếp Việt",
                "Đồ ăn cuối ngày",
                "MEAL",
                "store@example.com",
                "0912345678"
        );
        store.updateLocation(
                "1 Test Street",
                "Ben Nghe",
                "District 1",
                "Ho Chi Minh City",
                BigDecimal.valueOf(10.7769),
                BigDecimal.valueOf(106.7009)
        );
        merchantProfileRepository.save(store);
        openingHourRepository.save(new MerchantOpeningHour(
                store,
                DayOfWeek.MONDAY,
                LocalTime.of(8, 0),
                LocalTime.of(20, 0)
        ));
        createDeal("Available deal", DealStatus.ACTIVE, 3);
        createDeal("Hidden deal", DealStatus.HIDDEN, 3);
        createDeal("Sold out deal", DealStatus.ACTIVE, 0);
    }

    @Test
    void publicProfileContainsOpeningHoursCurrentDealsAndEmptyRatingSummary() {
        PublicStoreProfileResponse response = publicStoreService.getStore(
                merchant.getPublicId(),
                10.7768,
                106.7008,
                0,
                20
        );

        assertEquals("Bếp Việt", response.storeName());
        assertTrue(response.verified());
        assertEquals(1, response.openingHours().size());
        assertEquals(1, response.activeDeals().totalElements());
        assertEquals("Available deal", response.activeDeals().items().getFirst().name());
        assertTrue(response.activeDeals().items().getFirst().distanceKm()
                .compareTo(BigDecimal.ZERO) > 0);
        assertNull(response.rating().averageRating());
        assertEquals(0, response.rating().reviewCount());
    }

    private void createDeal(String name, DealStatus status, int quantity) {
        Deal deal = new Deal(
                store,
                name,
                "MEAL",
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(50_000),
                quantity,
                "https://cdn.lootfast.test/deal.jpg",
                10,
                NOW.plusSeconds(3_600),
                NOW.plusSeconds(7_200),
                NOW.minusSeconds(3_600),
                NOW.plusSeconds(7_200),
                status == DealStatus.HIDDEN ? DealStatus.ACTIVE : status
        );
        if (status == DealStatus.HIDDEN) {
            deal.hide(NOW);
        }
        dealRepository.save(deal);
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
