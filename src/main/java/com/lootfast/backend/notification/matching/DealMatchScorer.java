package com.lootfast.backend.notification.matching;

import com.lootfast.backend.notification.entity.DealMatchReason;
import com.lootfast.backend.notification.repository.MatchingDealRecipient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Pure scoring logic: turns a buyer's preference signals into a notify / don't-notify decision. */
@Component
public class DealMatchScorer {

    private final MatchingProperties properties;

    public DealMatchScorer(MatchingProperties properties) {
        this.properties = properties;
    }

    /**
     * @return the reason to show (the strongest signal present; ties keep the order below) and
     *         total score, or empty when the score is below the threshold or the buyer already
     *         reached today's limit.
     */
    public Optional<Match> score(MatchingDealRecipient candidate) {
        if (candidate.getSentToday() >= properties.dailyLimit()) {
            return Optional.empty();
        }
        MatchingProperties.Weights w = properties.weights();
        Accumulator acc = new Accumulator();
        acc.add(candidate.getFavoriteStore(), w.favoriteStore(), DealMatchReason.FAVORITE_STORE);
        acc.add(candidate.getFavoriteItem(), w.favoriteItem(), DealMatchReason.FAVORITE_ITEM);
        acc.add(candidate.getFavoriteCategory(), w.favoriteCategory(), DealMatchReason.FAVORITE_CATEGORY);
        acc.add(candidate.getOrderHistory(), w.orderHistory(), DealMatchReason.ORDER_HISTORY);
        acc.add(candidate.getNearbyArea(), w.nearbyArea(), DealMatchReason.NEARBY_AREA);
        acc.add(candidate.getBigDiscount(), w.bigDiscount(), DealMatchReason.BIG_DISCOUNT);
        if (acc.reason == null || acc.total < properties.threshold()) {
            return Optional.empty();
        }
        return Optional.of(new Match(acc.reason, acc.total));
    }

    public record Match(DealMatchReason reason, int score) {
    }

    private static final class Accumulator {
        private int total;
        private int strongest = -1;
        private DealMatchReason reason;

        void add(boolean present, int weight, DealMatchReason candidateReason) {
            if (!present) {
                return;
            }
            total += weight;
            if (weight > strongest) {
                strongest = weight;
                reason = candidateReason;
            }
        }
    }
}
