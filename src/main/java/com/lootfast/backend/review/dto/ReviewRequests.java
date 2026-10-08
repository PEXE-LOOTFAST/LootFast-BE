package com.lootfast.backend.review.dto;
import jakarta.validation.constraints.*;
public final class ReviewRequests {
  private ReviewRequests() {}
  public record Create(@NotNull @Min(1) @Max(5) Integer rating, @Size(max = 1000) String comment) {}
  public record Reply(@NotBlank @Size(max = 1000) String reply) {}
  public record Visibility(@NotNull Boolean hidden, @NotBlank @Size(max = 1000) String reason) {}
}
