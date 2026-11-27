package com.lootfast.backend.deal;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.deal.demo.DemoDeal;
import com.lootfast.backend.deal.demo.DemoDealConfig;
import com.lootfast.backend.deal.demo.DemoDealGenerator;
import com.lootfast.backend.deal.dto.DealDetailResponse;
import com.lootfast.backend.deal.dto.DealDiscoveryQuery;
import com.lootfast.backend.deal.dto.DealFeedItemResponse;
import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.deal.service.DealDetailService;
import com.lootfast.backend.deal.service.DealFeedService;
import com.lootfast.backend.deal.service.DealResponseMapper;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.review.service.StoreRatingService;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = "lootfast.demo-deals.enabled=true")
@Import({
        DealFeedService.class,
        DealDetailService.class,
        DealResponseMapper.class,
        DemoDealConfig.class,
        DemoDealGenerator.class,
        StoreRatingService.class,
        DemoDealFeedIntegrationTests.FixedClockConfig.class
})
class DemoDealFeedIntegrationTests {

    /** 11:00 in Ho Chi Minh City. */
    private static final Instant NOW = Instant.parse("2026-09-15T04:00:00Z");
    private static final double LATITUDE = 21.028_511;
    private static final double LONGITUDE = 105.804_817;

    @Autowired private UserRepository userRepository;
    @Autowired private MerchantProfileRepository merchantProfileRepository;
    @Autowired private DealRepository dealRepository;
    @Autowired private DealFeedService dealFeedService;
    @Autowired private DealDetailService dealDetailService;

    @Test
    void anAreaWithoutRealDealsGetsAtMostTwoLabelledDemoDealsSortedByDistance() {
        PagedResponse<DealFeedItemResponse> response = dealFeedService.getFeed(
                LATITUDE, LONGITUDE, 1.0, 0, 20
        );

        assertTrue(response.totalElements() >= 1 && response.totalElements() <= 2);
        BigDecimal previousDistance = BigDecimal.ZERO;
        for (DealFeedItemResponse item : response.items()) {
            assertTrue(item.demo());
            assertEquals(DemoDeal.NOTICE, item.demoNotice());
            assertTrue(item.merchant().storeName().startsWith("[DEMO] "));
            assertFalse(item.merchant().verified());
            assertNull(item.primaryImageUrl());
            assertTrue(item.distanceKm().compareTo(BigDecimal.ONE) <= 0);
            assertTrue(item.distanceKm().compareTo(previousDistance) >= 0);
            previousDistance = item.distanceKm();
        }
    }

    @Test
    void demoDealsDisappearOnceARealDealIsNearby() {
        createRealDeal(LATITUDE + 0.000_1, LONGITUDE + 0.000_1);

        PagedResponse<DealFeedItemResponse> response = dealFeedService.getFeed(
                LATITUDE, LONGITUDE, 1.0, 0, 20
        );

        assertEquals(1, response.totalElements());
        assertFalse(response.items().getFirst().demo());
        assertNull(response.items().getFirst().demoNotice());
    }

    @Test
    void demoDetailMatchesTheFeedItemAndReportsDistance() {
        DealFeedItemResponse listed = dealFeedService.getFeed(LATITUDE, LONGITUDE, 1.0, 0, 20)
                .items()
                .getFirst();

        DealDetailResponse detail = dealDetailService.getDetail(listed.dealId(), LATITUDE, LONGITUDE);

        assertTrue(detail.demo());
        assertEquals(DemoDeal.NOTICE, detail.demoNotice());
        assertEquals(listed.name(), detail.name());
        assertEquals(listed.discountedPrice(), detail.discountedPrice());
        assertEquals(listed.merchant().merchantId(), detail.merchant().merchantId());
        assertEquals(listed.distanceKm(), detail.distanceKm());
        assertEquals(DealStatus.ACTIVE, detail.status());
        assertEquals(0, detail.rating().reviewCount());
    }

    @Test
    void demoStoreIdsAndUnknownIdsAreNotDeals() {
        DealFeedItemResponse listed = dealFeedService.getFeed(LATITUDE, LONGITUDE, 1.0, 0, 20)
                .items()
                .getFirst();

        for (UUID id : new UUID[] {listed.merchant().merchantId(), UUID.randomUUID()}) {
            ApiException exception = assertThrows(
                    ApiException.class,
                    () -> dealDetailService.getDetail(id, null, null)
            );
            assertEquals(ErrorCode.DEAL_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Test
    void demoDealsHonourSearchAndCategoryFilters() {
        PagedResponse<DealFeedItemResponse> unmatchedKeyword = dealFeedService.getFeed(
                query("khong ton tai mon nay", null)
        );
        assertEquals(0, unmatchedKeyword.totalElements());

        DealFeedItemResponse listed = dealFeedService.getFeed(LATITUDE, LONGITUDE, 1.0, 0, 20)
                .items()
                .getFirst();
        PagedResponse<DealFeedItemResponse> sameCategory = dealFeedService.getFeed(
                query(null, listed.categoryCode().toLowerCase())
        );
        assertNotNull(sameCategory.items());
        assertFalse(sameCategory.items().isEmpty());
        assertTrue(sameCategory.items().stream()
                .allMatch(item -> item.categoryCode().equals(listed.categoryCode())));
    }

    @Test
    void laterPagesOfADemoFeedAreEmpty() {
        PagedResponse<DealFeedItemResponse> secondPage = dealFeedService.getFeed(
                LATITUDE, LONGITUDE, 1.0, 1, 20
        );

        assertTrue(secondPage.items().isEmpty());
        assertTrue(secondPage.totalElements() >= 1);
    }

    private DealDiscoveryQuery query(String keyword, String categoryCode) {
        return new DealDiscoveryQuery(
                LATITUDE, LONGITUDE, 1.0, keyword, categoryCode, null, null, null, 0, 20
        );
    }

    private void createRealDeal(double latitude, double longitude) {
        User merchantUser = userRepository.save(new User(
                "Real",
                "Store",
                UUID.randomUUID() + "@gmail.com",
                null,
                null,
                UserRole.MERCHANT,
                UserStatus.VERIFIED
        ));
        MerchantProfile profile = new MerchantProfile(merchantUser);
        profile.markVerified(NOW);
        profile.updateStoreInformation("Real Store", "Description", "BAKERY", null, null);
        profile.updateLocation(
                "1 Test Street",
                "Dien Bien",
                "Ba Dinh",
                "Ha Noi",
                BigDecimal.valueOf(latitude),
                BigDecimal.valueOf(longitude)
        );
        merchantProfileRepository.save(profile);
        dealRepository.save(new Deal(
                profile,
                "Real bakery",
                "BAKERY",
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(50_000),
                3,
                "https://cdn.lootfast.test/deal.jpg",
                10,
                NOW.plusSeconds(3_600),
                NOW.plusSeconds(7_200),
                NOW.minusSeconds(3_600),
                NOW.plusSeconds(7_200),
                DealStatus.ACTIVE
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
