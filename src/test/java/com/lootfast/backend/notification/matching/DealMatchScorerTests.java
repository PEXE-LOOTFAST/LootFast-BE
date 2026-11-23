package com.lootfast.backend.notification.matching;

import com.lootfast.backend.notification.entity.DealMatchReason;
import com.lootfast.backend.notification.repository.MatchingDealRecipient;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DealMatchScorerTests {

    private final DealMatchScorer scorer =
            new DealMatchScorer(new MatchingProperties(null, null, null, null, null, null, null));

    @Test
    void singleStrongFoodPreferenceIsEnough() {
        assertEquals(DealMatchReason.FAVORITE_STORE, reason(candidate().store()));
        assertEquals(DealMatchReason.FAVORITE_CATEGORY, reason(candidate().category()));
        assertEquals(DealMatchReason.FAVORITE_ITEM, reason(candidate().item()));
        assertEquals(DealMatchReason.ORDER_HISTORY, reason(candidate().history()));
    }

    @Test
    void weakSignalsNeedEachOther() {
        assertTrue(scorer.score(candidate().nearby()).isEmpty());
        assertTrue(scorer.score(candidate().discount()).isEmpty());
        assertEquals(DealMatchReason.NEARBY_AREA, reason(candidate().nearby().discount()));
    }

    @Test
    void reasonIsTheStrongestSignalAndScoreAddsUp() {
        var match = scorer.score(candidate().history().category().store().discount()).orElseThrow();
        assertEquals(DealMatchReason.FAVORITE_STORE, match.reason());
        assertEquals(50 + 40 + 30 + 10, match.score());
        // Equal weights keep declaration order: favourite dish before favourite category.
        assertEquals(DealMatchReason.FAVORITE_ITEM, reason(candidate().category().item()));
    }

    @Test
    void dailyLimitSuppressesEvenStrongMatches() {
        assertTrue(scorer.score(candidate().store().sentToday(5)).isEmpty());
        assertEquals(DealMatchReason.FAVORITE_STORE, reason(candidate().store().sentToday(4)));
    }

    @Test
    void customWeightsAndThresholdAreRespected() {
        var strict = new DealMatchScorer(new MatchingProperties(
                new MatchingProperties.Weights(null, null, null, null, 5, 5), 60, 1, null, null, null, null));
        assertTrue(strict.score(candidate().store()).isEmpty());
        assertEquals(Optional.of(DealMatchReason.FAVORITE_STORE),
                strict.score(candidate().store().category()).map(DealMatchScorer.Match::reason));
    }

    private DealMatchReason reason(Candidate candidate) {
        return scorer.score(candidate).orElseThrow().reason();
    }

    private static Candidate candidate() {
        return new Candidate();
    }

    private static final class Candidate implements MatchingDealRecipient {
        private boolean store, category, item, history, nearby, discount;
        private long sentToday;

        Candidate store() { store = true; return this; }
        Candidate category() { category = true; return this; }
        Candidate item() { item = true; return this; }
        Candidate history() { history = true; return this; }
        Candidate nearby() { nearby = true; return this; }
        Candidate discount() { discount = true; return this; }
        Candidate sentToday(long value) { sentToday = value; return this; }

        @Override public Long getUserId() { return 1L; }
        @Override public String getEmail() { return "buyer@example.com"; }
        @Override public boolean getEmailEnabled() { return false; }
        @Override public boolean getFavoriteStore() { return store; }
        @Override public boolean getFavoriteCategory() { return category; }
        @Override public boolean getFavoriteItem() { return item; }
        @Override public boolean getOrderHistory() { return history; }
        @Override public boolean getNearbyArea() { return nearby; }
        @Override public boolean getBigDiscount() { return discount; }
        @Override public long getSentToday() { return sentToday; }
    }
}
