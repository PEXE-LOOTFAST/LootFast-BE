package com.lootfast.backend.notification.repository;

public interface MatchingDealRecipient {
    Long getUserId();

    String getEmail();

    boolean getEmailEnabled();

    String getMatchReason();
}
