package com.lootfast.backend.merchant.sanction.dto;

import com.lootfast.backend.merchant.sanction.entity.MerchantSanction;
import com.lootfast.backend.merchant.sanction.entity.SanctionType;
import java.time.Instant;
import java.util.UUID;

public record MerchantSanctionResponse(
    UUID sanctionId,
    UUID storeId,
    SanctionType type,
    String reason,
    Instant startsAt,
    Instant endsAt,
    Instant liftedAt,
    boolean active,
    Instant createdAt) {
  public static MerchantSanctionResponse from(MerchantSanction s, Instant now) {
    return new MerchantSanctionResponse(
        s.getPublicId(),
        s.getMerchant().getUser().getPublicId(),
        s.getSanctionType(),
        s.getReason(),
        s.getStartsAt(),
        s.getEndsAt(),
        s.getLiftedAt(),
        s.isActive(now),
        s.getCreatedAt());
  }
}
