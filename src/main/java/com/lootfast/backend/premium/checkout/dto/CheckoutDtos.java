package com.lootfast.backend.premium.checkout.dto;

import com.lootfast.backend.premium.catalog.dto.PlanResponse;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class CheckoutDtos {
  private CheckoutDtos() {}

  public record Create(
      @NotNull UUID planRevisionId,
      @NotNull UUID termsDocumentId,
      @AssertTrue boolean acceptTerms) {}

  public record Order(
      UUID id,
      String status,
      PlanResponse plan,
      Instant acceptedAt,
      Instant createdAt,
      Instant expiresAt,
      Attempt attempt) {}

  public record Attempt(
      UUID id, String reference, String status, Instant createdAt, Instant expiresAt) {}

  public record Payment(UUID orderId, Attempt attempt, String paymentUrl) {
    @Override
    public String toString() {
      return "Payment[orderId=" + orderId + ",paymentUrl=REDACTED]";
    }
  }
}
