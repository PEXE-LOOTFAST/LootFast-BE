package com.lootfast.backend.favorite;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.deal.service.DealResponseMapper;
import com.lootfast.backend.favorite.dto.FavoriteItemResponse;
import com.lootfast.backend.favorite.repository.UserFavoriteRepository;
import com.lootfast.backend.favorite.service.FavoriteService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Import({
        FavoriteService.class,
        DealResponseMapper.class,
        com.lootfast.backend.review.service.StoreRatingService.class,
        FavoriteServiceIntegrationTests.FixedClockConfig.class
})
class FavoriteServiceIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-09-15T04:00:00Z");

    @Autowired private UserRepository userRepository;
    @Autowired private MerchantProfileRepository merchantProfileRepository;
    @Autowired private DealRepository dealRepository;
    @Autowired private UserFavoriteRepository favoriteRepository;
    @Autowired private FavoriteService favoriteService;

    private User buyer;
    private List<StoreDeal> storeDeals;

    @BeforeEach
    void setUp() {
        buyer = userRepository.save(new User(
                "Buyer",
                "User",
                "buyer-" + UUID.randomUUID() + "@example.com",
                null,
                null,
                UserRole.BUYER,
                UserStatus.VERIFIED
        ));
        storeDeals = new ArrayList<>();
        for (int index = 1; index <= 4; index++) {
            storeDeals.add(createStoreDeal(index));
        }
    }

    @Test
    void favoritesAreIdempotentCombinedAndLimitedForFreeAccounts() {
        String subject = buyer.getId().toString();
        FavoriteItemResponse first = favoriteService.saveDeal(
                subject,
                storeDeals.get(0).deal().getPublicId()
        );
        FavoriteItemResponse repeated = favoriteService.saveDeal(
                subject,
                storeDeals.get(0).deal().getPublicId()
        );
        favoriteService.saveStore(
                subject,
                storeDeals.get(1).store().getUser().getPublicId()
        );
        favoriteService.saveDeal(subject, storeDeals.get(2).deal().getPublicId());

        assertEquals(first.savedAt(), repeated.savedAt());
        assertEquals(3, favoriteRepository.countByUserId(buyer.getId()));
        PagedResponse<FavoriteItemResponse> page = favoriteService.getFavorites(
                subject,
                0,
                20
        );
        assertEquals(3, page.totalElements());

        ApiException exception = assertThrows(ApiException.class, () ->
                favoriteService.saveStore(
                        subject,
                        storeDeals.get(3).store().getUser().getPublicId()
                )
        );
        assertEquals(ErrorCode.FAVORITE_LIMIT_REACHED, exception.getErrorCode());

        favoriteService.removeDeal(subject, storeDeals.get(0).deal().getPublicId());
        favoriteService.saveStore(
                subject,
                storeDeals.get(3).store().getUser().getPublicId()
        );
        assertEquals(3, favoriteRepository.countByUserId(buyer.getId()));
    }

    private StoreDeal createStoreDeal(int index) {
        User merchant = userRepository.save(new User(
                "Merchant",
                Integer.toString(index),
                "merchant-" + index + "-" + UUID.randomUUID() + "@example.com",
                null,
                null,
                UserRole.MERCHANT,
                UserStatus.VERIFIED
        ));
        MerchantProfile store = new MerchantProfile(merchant);
        store.updateStoreInformation(
                "Store " + index,
                "Description",
                "BAKERY",
                null,
                null
        );
        store.updateLocation(
                index + " Test Street",
                "Ben Nghe",
                "District 1",
                "Ho Chi Minh City",
                BigDecimal.valueOf(10.7769 + index * 0.0001),
                BigDecimal.valueOf(106.7009 + index * 0.0001)
        );
        merchantProfileRepository.save(store);
        Deal deal = dealRepository.save(new Deal(
                store,
                "Deal " + index,
                "BAKERY",
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(50_000),
                5,
                "https://cdn.lootfast.test/deal-" + index + ".jpg",
                10,
                NOW.plusSeconds(3_600),
                NOW.plusSeconds(7_200),
                NOW.minusSeconds(3_600),
                NOW.plusSeconds(7_200),
                DealStatus.ACTIVE
        ));
        return new StoreDeal(store, deal);
    }

    private record StoreDeal(MerchantProfile store, Deal deal) {
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
