package com.lootfast.backend.deal;

import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.deal.dto.DealDetailResponse;
import com.lootfast.backend.deal.dto.DealDiscoveryQuery;
import com.lootfast.backend.deal.dto.DealFeedItemResponse;
import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.deal.service.DealFeedService;
import com.lootfast.backend.deal.service.DealDetailService;
import com.lootfast.backend.deal.service.DealResponseMapper;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({
        DealFeedService.class,
        DealDetailService.class,
        DealResponseMapper.class,
        DealFeedIntegrationTests.FixedClockConfig.class
})
class DealFeedIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-09-15T04:00:00Z");

    @Autowired private jakarta.persistence.EntityManager entityManager;
    @Autowired private UserRepository userRepository;
    @Autowired private MerchantProfileRepository merchantProfileRepository;
    @Autowired private DealRepository dealRepository;
    @Autowired private DealFeedService dealFeedService;
    @Autowired private DealDetailService dealDetailService;

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
    void feedHandlesEachOptionalFilterIndependently() {
        var queries = java.util.List.of(
                new DealDiscoveryQuery(10.776889, 106.700806, 1.0,
                        "bakery", null, null, null, null, 0, 20),
                new DealDiscoveryQuery(10.776889, 106.700806, 1.0,
                        null, "BAKERY", null, null, null, 0, 20),
                new DealDiscoveryQuery(10.776889, 106.700806, 1.0,
                        null, null, 40, null, null, 0, 20),
                new DealDiscoveryQuery(10.776889, 106.700806, 1.0,
                        null, null, null, NOW.plusSeconds(4_000), null, 0, 20),
                new DealDiscoveryQuery(10.776889, 106.700806, 1.0,
                        null, null, null, null, NOW.plusSeconds(6_000), 0, 20)
        );
        for (var query : queries) {
            assertEquals(2, dealFeedService.getFeed(query).totalElements());
        }
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

    @Test
    void feedFiltersAndSearchesVietnameseTextWithoutAccents() {
        createDeal(
                "Hộp cơm trưa",
                "Bếp Việt Quán",
                "MEAL",
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(30_000),
                NOW.plusSeconds(14_400),
                NOW.plusSeconds(18_000),
                DealStatus.ACTIVE,
                5
        );

        PagedResponse<DealFeedItemResponse> response = dealFeedService.getFeed(
                new DealDiscoveryQuery(
                        10.776889,
                        106.700806,
                        1.0,
                        "bep viet",
                        " meal ",
                        60,
                        NOW.plusSeconds(12_600),
                        NOW.plusSeconds(16_200),
                        0,
                        20
                )
        );

        assertEquals(1, response.totalElements());
        assertEquals("Hộp cơm trưa", response.items().getFirst().name());
        assertEquals(70, response.items().getFirst().discountPercent());
    }

    @Test
    void feedRejectsAnInvalidPickupWindow() {
        ApiException exception = assertThrows(ApiException.class, () ->
                dealFeedService.getFeed(new DealDiscoveryQuery(
                        10.776889,
                        106.700806,
                        1.0,
                        null,
                        null,
                        null,
                        NOW.plusSeconds(100),
                        NOW,
                        0,
                        20
                ))
        );

        assertEquals(ErrorCode.INVALID_DEAL_FILTER, exception.getErrorCode());
    }

    @Test
    void detailReturnsCurrentInventoryAndOptionalDistance() {
        Deal deal = createDeal(
                "Realtime deal",
                10.776950,
                106.700950,
                DealStatus.ACTIVE,
                7
        );

        DealDetailResponse response = dealDetailService.getDetail(
                deal.getPublicId(),
                10.776889,
                106.700806
        );

        assertEquals(7, response.remainingQuantity());
        assertEquals(0, response.inventoryVersion());
        assertTrue(response.distanceKm().compareTo(BigDecimal.ZERO) > 0);
        assertNull(response.rating().averageRating());
        assertEquals(0, response.rating().reviewCount());
    }

    @Test
    void globalFeedIncludesDistantDealsWithNullDistanceAndStableDatabasePagination() {
        createDeal("Hanoi bakery", 21.0285, 105.8542, DealStatus.ACTIVE, 2);
        createDeal("Draft bakery", 21.0285, 105.8542, DealStatus.DRAFT, 2);
        createDeal("Removed bakery", 21.0285, 105.8542, DealStatus.REMOVED, 2);
        var first = dealFeedService.getFeed(globalQuery(null, null, null, null, null, 0, 1));
        var second = dealFeedService.getFeed(globalQuery(null, null, null, null, null, 1, 1));
        assertEquals(3, first.totalElements());
        assertEquals(3, first.totalPages());
        assertEquals("Hanoi bakery", first.items().getFirst().name());
        assertNull(first.items().getFirst().distanceKm());
        assertEquals("Far bakery", second.items().getFirst().name());
        assertTrue(dealFeedService.getFeed(globalQuery(null,null,null,null,null,3,1)).items().isEmpty());
    }

    @Test
    void globalFeedRetainsEachOptionalFilterAndExcludesExpiredAndFutureDeals() {
        for (var query : java.util.List.of(
                globalQuery("near",null,null,null,null,0,1),
                globalQuery(null,"BAKERY",null,null,null,0,1),
                globalQuery(null,null,40,null,null,0,1),
                globalQuery(null,null,null,NOW.plusSeconds(4000),null,0,1),
                globalQuery(null,null,null,null,NOW.plusSeconds(6000),0,1))) {
            assertEquals(query.keyword() == null ? 2 : 1, dealFeedService.getFeed(query).totalElements());
        }
        var expired = createDeal("Expired bakery",10.7,106.7,DealStatus.ACTIVE,2);
        var future = createDeal("Future bakery",10.7,106.7,DealStatus.ACTIVE,2);
        dealRepository.flush();
        // Change only fixture sale times; production migration/data is untouched.
        entityManager.createNativeQuery("update deals set available_until = :end where id = :id")
                .setParameter("end", NOW.minusSeconds(1)).setParameter("id",expired.getId()).executeUpdate();
        entityManager.createNativeQuery("update deals set available_from = :start where id = :id")
                .setParameter("start", NOW.plusSeconds(3600)).setParameter("id",future.getId()).executeUpdate();
        entityManager.clear();
        assertEquals(2,dealFeedService.getFeed(globalQuery(null,null,null,null,null,0,20)).totalElements());
    }

    @Test
    void feedRejectsPartialCoordinatesAndInvalidPagination() {
        for (var query : java.util.List.of(
                new DealDiscoveryQuery(10.7,null,1,null,null,null,null,null,0,20),
                new DealDiscoveryQuery(null,106.7,1,null,null,null,null,null,0,20),
                globalQuery(null,null,null,null,null,-1,20),
                globalQuery(null,null,null,null,null,0,51))) {
            assertEquals(ErrorCode.INVALID_DEAL_FILTER,
                    assertThrows(ApiException.class, () -> dealFeedService.getFeed(query)).getErrorCode());
        }
    }

    private DealDiscoveryQuery globalQuery(String keyword, String category, Integer discount,
            Instant from, Instant to, int page, int size) {
        return new DealDiscoveryQuery(null,null,1,keyword,category,discount,from,to,page,size);
    }

    private Deal createDeal(
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
        profile.markVerified(NOW);
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
        Deal deal = new Deal(
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
                status == DealStatus.HIDDEN || status == DealStatus.REMOVED ? DealStatus.ACTIVE : status
        );
        if (status == DealStatus.HIDDEN) {
            deal.hide(NOW);
        } else if (status == DealStatus.REMOVED) {
            deal.remove(merchantUser, "No longer available", NOW);
        }
        return dealRepository.save(deal);
    }

    private Deal createDeal(
            String name,
            String storeName,
            String categoryCode,
            BigDecimal originalPrice,
            BigDecimal discountedPrice,
            Instant pickupStart,
            Instant pickupEnd,
            DealStatus status,
            int remainingQuantity
    ) {
        User merchantUser = userRepository.save(new User(
                "Merchant",
                "Store",
                UUID.randomUUID() + "@gmail.com",
                null,
                null,
                UserRole.MERCHANT,
                UserStatus.VERIFIED
        ));
        MerchantProfile profile = new MerchantProfile(merchantUser);
        profile.markVerified(NOW);
        profile.updateStoreInformation(storeName, "Description", categoryCode, null, null);
        profile.updateLocation(
                "1 Test Street",
                "Ben Nghe",
                "District 1",
                "Ho Chi Minh City",
                BigDecimal.valueOf(10.776950),
                BigDecimal.valueOf(106.700950)
        );
        merchantProfileRepository.save(profile);
        Deal deal = new Deal(
                profile,
                name,
                categoryCode,
                originalPrice,
                discountedPrice,
                remainingQuantity,
                "https://cdn.lootfast.test/deal.jpg",
                10,
                pickupStart,
                pickupEnd,
                NOW.minusSeconds(3_600),
                pickupEnd,
                status == DealStatus.HIDDEN || status == DealStatus.REMOVED ? DealStatus.ACTIVE : status
        );
        if (status == DealStatus.HIDDEN) {
            deal.hide(NOW);
        } else if (status == DealStatus.REMOVED) {
            deal.remove(merchantUser, "No longer available", NOW);
        }
        return dealRepository.save(deal);
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
