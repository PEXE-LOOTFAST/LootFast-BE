package com.lootfast.backend.premium.catalog.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lootfast.backend.premium.dto.PremiumBenefit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Set;
import java.util.UUID;

public final class PlanRequests {
  private PlanRequests() {}

  public record Configuration(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Size(max = 2000) String description,
      @NotNull @Min(1000) @Max(999999999) @JsonDeserialize(using = VndAmountDeserializer.class)
          Long priceVnd,
      @NotNull @Min(1) @Max(366) Integer durationDays,
      @NotEmpty @Size(max = 8) Set<@NotNull PremiumBenefit> benefits,
      @NotNull UUID termsDocumentId) {}

  public record Create(
      @NotBlank @Pattern(regexp = "[A-Z0-9_]{1,64}") String code,
      @NotNull PlanAudience audience,
      @Valid @NotNull Configuration configuration) {}

  public record Edit(
      @NotNull @Min(0) Long expectedRevision, @Valid @NotNull Configuration configuration) {}

  public record Change(
      @NotNull @Min(0) Long expectedRevision, @NotBlank @Size(max = 500) String reason) {}
}
