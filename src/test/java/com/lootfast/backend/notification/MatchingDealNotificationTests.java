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
import com.lootfast.backend.notification.event.NotificationCreatedEvent;
import com.lootfast.backend.notification.matching.DealMatchJobRepository;
import com.lootfast.backend.notification.repository.NotificationPreferenceRepository;
import com.lootfast.backend.notification.repository.UserNotificationRepository;
import com.lootfast.backend.notification.service.MatchingDealNotificationService;
import com.lootfast.backend.order.entity.CustomerOrder;
import com.lootfast.backend.order.entity.PaymentMethod;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserProfile;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserProfileRepository;
import com.lootfast.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against real PostgreSQL (Flyway schema) because matching relies on native SQL:
 * keyset pagination, {@code ON CONFLICT} and {@code FOR UPDATE SKIP LOCKED}.
 * A batch size of 2 forces every scenario through several keyset pages.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "lootfast.notifications.matching.batch-size=2"
})
@Transactional
@RecordApplicationEvents
abstract class MatchingDealNotificationTests {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private MatchingDealNotificationService matchingService;
    @Autowired private DealMatchJobRepository jobRepository;
    @Autowired private UserNotificationRepository notificationRepository;
    @Autowired private NotificationPreferenceRepository preferenceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository profileRepository;
    @Autowired private MerchantProfileRepository merchantRepository;
    @Autowired private DealRepository dealRepository;
    @Autowired private UserFavoriteRepository favoriteRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationEvents applicationEvents;

    @Test
    void scoresFoodPreferencesAndNotifiesOnlyBuyersAboveThreshold() {
        MerchantProfile store = store("Evening Bakery", "District 1");
        MerchantProfile otherStore = store("Corner Bakery", "District 7");
        Deal deal = deal(store, "Bread combo", "BAKERY", 40_000, DealStatus.ACTIVE);

        User favoriteStore = buyer();
        favoriteRepository.save(UserFavorite.store(favoriteStore, store));
        preference(favoriteStore, true, true);

        User favoriteCategory = buyer();
        profile(favoriteCategory, null, Set.of("BAKERY"));

        User favoriteItem = buyer();
        favoriteRepository.save(UserFavorite.deal(favoriteItem,
                deal(otherStore, "Bread combo", "MEAL", 90_000, DealStatus.SOLD_OUT)));

        User orderHistory = buyer();
        Deal pastBakeryDeal = deal(otherStore, "Croissant box", "BAKERY", 90_000, DealStatus.ACTIVE);
        completedOrder(orderHistory, pastBakeryDeal, Instant.now().minus(Duration.ofDays(3)));
        completedOrder(orderHistory, pastBakeryDeal, Instant.now().minus(Duration.ofDays(2)));

        User singleOldOrder = buyer();
        completedOrder(singleOldOrder, pastBakeryDeal, Instant.now().minus(Duration.ofDays(2)));
        completedOrder(singleOldOrder, pastBakeryDeal, Instant.now().minus(Duration.ofDays(45)));

        User nearbyWithDiscount = buyer();
        profile(nearbyWithDiscount, "district 1", Set.of());

        User discountOnly = buyer();

        User capped = buyer();
        favoriteRepository.save(UserFavorite.store(capped, store));
        for (int i = 0; i < 5; i++) {
            existingMatchingNotification(capped, Instant.now());
        }

        User disabled = buyer();
        favoriteRepository.save(UserFavorite.store(disabled, store));
        preference(disabled, false, true);

        matchingService.enqueue(deal);
        assertEquals(0, notificationsFor(deal).size(), "publish must only enqueue");

        int batches = drainJobs();

        Map<Long, DealMatchReason> reasons = notificationsFor(deal);
        assertEquals(Map.of(
                favoriteStore.getId(), DealMatchReason.FAVORITE_STORE,
                favoriteCategory.getId(), DealMatchReason.FAVORITE_CATEGORY,
                favoriteItem.getId(), DealMatchReason.FAVORITE_ITEM,
                orderHistory.getId(), DealMatchReason.ORDER_HISTORY,
                nearbyWithDiscount.getId(), DealMatchReason.NEARBY_AREA
        ), reasons);
        assertTrue(batches > 2, "keyset paging should take several batches, took " + batches);

        var job = jobRepository.findById(deal.getId()).orElseThrow();
        assertNotNull(job.getCompletedAt());
        assertEquals(5, applicationEvents.stream(NotificationCreatedEvent.class)
                .filter(event -> deal.getPublicId().equals(event.notification().dealId()))
                .count());
        assertEquals(1, applicationEvents.stream(MatchingDealEmailEvent.class).count());

        // Re-enqueueing and re-running never duplicates notifications.
        matchingService.enqueue(deal);
        drainJobs();
        assertEquals(5, notificationsFor(deal).size());
    }

    @Test
    void skipsBuyersAlreadyNotifiedForTheDealWithoutPushingAgain() {
        MerchantProfile store = store("Dedup Store", "District 3");
        Deal deal = deal(store, "Rice box", "MEAL", 30_000, DealStatus.ACTIVE);
        User buyer = buyer();
        favoriteRepository.save(UserFavorite.store(buyer, store));
        jdbc.update("""
                insert into notifications(public_id, recipient_user_id, type, match_reason, deal_public_id,
                    store_public_id, title, message, created_at)
                values (?, ?, 'MATCHING_DEAL', 'SAVED_FILTER', ?, ?, 't', 'm', ?)
                """, UUID.randomUUID(), buyer.getId(), deal.getPublicId(),
                store.getUser().getPublicId(), Timestamp.from(Instant.now()));

        matchingService.enqueue(deal);
        drainJobs();

        assertEquals(Map.of(buyer.getId(), DealMatchReason.SAVED_FILTER), notificationsFor(deal));
        assertEquals(0, applicationEvents.stream(NotificationCreatedEvent.class).count());
    }

    @Test
    void weakSignalsAloneAndInactiveDealsDoNotNotify() {
        MerchantProfile store = store("Threshold Store", "District 5");
        User nearbyOnly = buyer();
        profile(nearbyOnly, "District 5", Set.of());
        buyer(); // no preference at all

        Deal exactlyHalfOff = deal(store, "Half price", "MEAL", 50_000, DealStatus.ACTIVE);
        matchingService.enqueue(exactlyHalfOff);
        drainJobs();
        assertTrue(notificationsFor(exactlyHalfOff).isEmpty());

        User favoriteStore = buyer();
        favoriteRepository.save(UserFavorite.store(favoriteStore, store));
        Deal soldOut = deal(store, "Sold out deal", "MEAL", 10_000, DealStatus.SOLD_OUT);
        matchingService.enqueue(soldOut);
        drainJobs();
        assertTrue(notificationsFor(soldOut).isEmpty());
        assertNotNull(jobRepository.findById(soldOut.getId()).orElseThrow().getCompletedAt());
    }

    private int drainJobs() {
        int batches = 0;
        while (matchingService.processNextJob()) {
            batches++;
            assertTrue(batches < 100, "job did not finish");
        }
        return batches;
    }

    private Map<Long, DealMatchReason> notificationsFor(Deal deal) {
        return notificationRepository.findAll().stream()
                .filter(notification -> deal.getPublicId().equals(notification.getDealPublicId()))
                .collect(Collectors.toMap(
                        notification -> notification.getRecipient().getId(),
                        notification -> notification.getMatchReason()
                ));
    }

    private void existingMatchingNotification(User recipient, Instant createdAt) {
        jdbc.update("""
                insert into notifications(public_id, recipient_user_id, type, match_reason, deal_public_id,
                    store_public_id, title, message, created_at)
                values (?, ?, 'MATCHING_DEAL', 'BIG_DISCOUNT', ?, ?, 't', 'm', ?)
                """, UUID.randomUUID(), recipient.getId(), UUID.randomUUID(), UUID.randomUUID(),
                Timestamp.from(createdAt));
    }

    private void completedOrder(User buyer, Deal deal, Instant at) {
        CustomerOrder order = new CustomerOrder(buyer, deal, 1, PaymentMethod.CASH, UUID.randomUUID(), "test", at);
        order.collectCash(deal.getMerchant().getUser(), at);
        order.complete(at);
        orderRepository.save(order);
    }

    private void preference(User user, boolean matching, boolean email) {
        NotificationPreference preference = new NotificationPreference(user);
        preference.update(matching, true, true, true, email);
        preferenceRepository.save(preference);
    }

    private void profile(User user, String pickupArea, Set<String> categories) {
        UserProfile profile = new UserProfile(user, "Buyer");
        profile.updateDiscoveryPreferences(pickupArea, categories);
        profileRepository.save(profile);
    }

    private MerchantProfile store(String name, String district) {
        MerchantProfile store = new MerchantProfile(user(UserRole.MERCHANT));
        store.updateStoreInformation(name, null, "BAKERY", null, null);
        store.updateLocation("1 Test Street", null, district, "Ho Chi Minh City",
                BigDecimal.valueOf(10.77), BigDecimal.valueOf(106.70));
        return merchantRepository.save(store);
    }

    private User buyer() {
        return user(UserRole.BUYER);
    }

    private User user(UserRole role) {
        int n = SEQUENCE.incrementAndGet();
        return userRepository.save(new User("Match", "Tester", "match-%d@example.com".formatted(n),
                "54%07d".formatted(n), "encoded-password", role, UserStatus.VERIFIED));
    }

    private Deal deal(MerchantProfile store, String name, String category, long discountedPrice, DealStatus status) {
        return dealRepository.saveAndFlush(new Deal(
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
                status
        ));
    }
}
