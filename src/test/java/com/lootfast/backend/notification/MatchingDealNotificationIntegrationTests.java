package com.lootfast.backend.notification;

import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.favorite.entity.UserFavorite;
import com.lootfast.backend.favorite.repository.UserFavoriteRepository;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.notification.entity.DealMatchReason;
import com.lootfast.backend.notification.entity.NotificationPreference;
import com.lootfast.backend.notification.event.MatchingDealEmailEvent;
import com.lootfast.backend.notification.repository.NotificationPreferenceRepository;
import com.lootfast.backend.notification.repository.UserNotificationRepository;
import com.lootfast.backend.notification.service.MatchingDealNotificationService;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserProfile;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserProfileRepository;
import com.lootfast.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@Transactional
@RecordApplicationEvents
class MatchingDealNotificationIntegrationTests {

    @Autowired private MatchingDealNotificationService matchingService;
    @Autowired private UserNotificationRepository notificationRepository;
    @Autowired private NotificationPreferenceRepository preferenceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository profileRepository;
    @Autowired private MerchantProfileRepository merchantRepository;
    @Autowired private DealRepository dealRepository;
    @Autowired private UserFavoriteRepository favoriteRepository;
    @Autowired private ApplicationEvents applicationEvents;

    @Test
    void createsOneNotificationPerBuyerWithDeterministicReasonAndHonorsPreferences() {
        User merchantUser = saveUser(
                "merchant-notification@example.com",
                "510000001",
                UserRole.MERCHANT
        );
        MerchantProfile store = new MerchantProfile(merchantUser);
        store.updateStoreInformation(
                "Evening Bakery",
                "Fresh surplus food",
                "BAKERY",
                "store@example.com",
                "+84900000000"
        );
        store.updateLocation(
                "1 Test Street",
                "Ben Nghe",
                "District 1",
                "Ho Chi Minh City",
                BigDecimal.valueOf(10.7769),
                BigDecimal.valueOf(106.7009)
        );
        store = merchantRepository.save(store);

        User favoriteStoreBuyer = saveBuyer("favorite-store@example.com", "510000002");
        favoriteRepository.save(UserFavorite.store(favoriteStoreBuyer, store));
        NotificationPreference emailPreference = new NotificationPreference(favoriteStoreBuyer);
        emailPreference.update(true, true, true, true, true);
        preferenceRepository.save(emailPreference);

        User favoriteItemBuyer = saveBuyer("favorite-item@example.com", "510000003");
        UserProfile favoriteItemProfile = new UserProfile(favoriteItemBuyer, "Favorite item");
        favoriteItemProfile.updateDiscoveryPreferences(null, Set.of("BAKERY"));
        profileRepository.save(favoriteItemProfile);

        User nearbyBuyer = saveBuyer("nearby@example.com", "510000004");
        UserProfile nearbyProfile = new UserProfile(nearbyBuyer, "Nearby buyer");
        nearbyProfile.updateDiscoveryPreferences("District 1", Set.of());
        profileRepository.save(nearbyProfile);

        User bigDiscountBuyer = saveBuyer("discount@example.com", "510000005");
        User disabledBuyer = saveBuyer("disabled@example.com", "510000006");
        NotificationPreference disabledPreference = new NotificationPreference(disabledBuyer);
        disabledPreference.update(false, true, true, true, true);
        preferenceRepository.save(disabledPreference);

        Deal deal = dealRepository.saveAndFlush(deal(store, "Bread combo", "BAKERY", 40_000));

        matchingService.createForPublishedDeal(deal);
        matchingService.createForPublishedDeal(deal);
        notificationRepository.flush();

        assertEquals(4, notificationRepository.count());
        Map<String, DealMatchReason> reasonsByEmail = notificationRepository.findAll().stream()
                .collect(Collectors.toMap(
                        notification -> notification.getRecipient().getEmail(),
                        notification -> notification.getMatchReason()
                ));
        assertEquals(DealMatchReason.FAVORITE_STORE,
                reasonsByEmail.get(favoriteStoreBuyer.getEmail()));
        assertEquals(DealMatchReason.FAVORITE_ITEM,
                reasonsByEmail.get(favoriteItemBuyer.getEmail()));
        assertEquals(DealMatchReason.NEARBY_AREA, reasonsByEmail.get(nearbyBuyer.getEmail()));
        assertEquals(DealMatchReason.BIG_DISCOUNT,
                reasonsByEmail.get(bigDiscountBuyer.getEmail()));
        assertFalse(reasonsByEmail.containsKey(disabledBuyer.getEmail()));
        assertFalse(reasonsByEmail.containsKey(merchantUser.getEmail()));
        assertEquals(1, applicationEvents.stream(MatchingDealEmailEvent.class).count());
    }

    @Test
    void discountMustBeStrictlyAboveFiftyPercentWithoutAnotherMatch() {
        User merchantUser = saveUser(
                "merchant-threshold@example.com",
                "520000001",
                UserRole.MERCHANT
        );
        MerchantProfile store = new MerchantProfile(merchantUser);
        store.updateStoreInformation("Threshold Store", null, "MEAL", null, null);
        store.updateLocation(
                "2 Test Street",
                null,
                "District 3",
                "Ho Chi Minh City",
                BigDecimal.valueOf(10.78),
                BigDecimal.valueOf(106.68)
        );
        store = merchantRepository.save(store);
        saveBuyer("threshold-buyer@example.com", "520000002");

        Deal exactlyFiftyPercent = dealRepository.saveAndFlush(
                deal(store, "Exact half", "MEAL", 50_000)
        );
        matchingService.createForPublishedDeal(exactlyFiftyPercent);

        assertEquals(0, notificationRepository.count());
    }

    private User saveBuyer(String email, String phoneNumber) {
        return saveUser(email, phoneNumber, UserRole.BUYER);
    }

    private User saveUser(String email, String phoneNumber, UserRole role) {
        return userRepository.save(new User(
                "Notification",
                "Tester",
                email,
                phoneNumber,
                "encoded-password",
                role,
                UserStatus.VERIFIED
        ));
    }

    private Deal deal(
            MerchantProfile store,
            String name,
            String category,
            long discountedPrice
    ) {
        return new Deal(
                store,
                name,
                category,
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(discountedPrice),
                10,
                "https://cdn.lootfast.test/deal.png",
                10,
                Instant.parse("2030-01-02T09:00:00Z"),
                Instant.parse("2030-01-02T10:00:00Z"),
                Instant.parse("2030-01-02T01:00:00Z"),
                Instant.parse("2030-01-02T08:00:00Z"),
                DealStatus.ACTIVE
        );
    }
}
