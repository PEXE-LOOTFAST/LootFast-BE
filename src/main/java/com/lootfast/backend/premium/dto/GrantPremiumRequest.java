package com.lootfast.backend.premium.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record GrantPremiumRequest(
    @NotNull UUID id,
    @NotNull UUID userId,
    @NotNull Instant startsAt,
    @NotNull Instant endsAt,
    @NotBlank @Size(max = 500) String reason,
    @Schema(
            defaultValue = "MERCHANT_ADVANCED_REPORTS",
            description = "Quyền cần cấp: báo cáo Merchant hoặc bộ lọc đã lưu Buyer")
        PremiumBenefit benefit) {
  public GrantPremiumRequest {
    if (benefit == null) benefit = PremiumBenefit.MERCHANT_ADVANCED_REPORTS;
  }

  public GrantPremiumRequest(
      UUID id, UUID userId, Instant startsAt, Instant endsAt, String reason) {
    this(id, userId, startsAt, endsAt, reason, PremiumBenefit.MERCHANT_ADVANCED_REPORTS);
  }
}
