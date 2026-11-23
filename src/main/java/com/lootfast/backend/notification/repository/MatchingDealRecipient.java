package com.lootfast.backend.notification.repository;

/** One buyer with at least one preference signal for a deal (native-query projection). */
public interface MatchingDealRecipient {
    Long getUserId();

    String getEmail();

    boolean getEmailEnabled();

    boolean getFavoriteStore();

    boolean getFavoriteCategory();

    boolean getFavoriteItem();

    boolean getOrderHistory();

    boolean getNearbyArea();

    boolean getBigDiscount();

    /** MATCHING_DEAL notifications already received since the start of the local day. */
    long getSentToday();
}
