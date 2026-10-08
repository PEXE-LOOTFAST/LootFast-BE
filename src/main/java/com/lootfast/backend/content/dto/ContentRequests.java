package com.lootfast.backend.content.dto;

import com.lootfast.backend.user.entity.ConsentDocument;
import jakarta.validation.constraints.*;
import java.time.Instant;

public final class ContentRequests {
  private ContentRequests() {}

  public record CategoryCreate(
      @NotBlank @Size(max = 100) String code,
      @NotBlank @Size(max = 160) String name,
      boolean enabled,
      @Min(0) @Max(10000) int sortOrder) {}

  public record CategoryEdit(
      @NotBlank @Size(max = 160) String name,
      boolean enabled,
      @Min(0) @Max(10000) int sortOrder,
      @NotNull @Min(0) Long expectedRevision) {}

  public record BannerWrite(
      @NotBlank @Size(max = 200) String title,
      @NotBlank @Size(max = 200) String altText,
      @NotBlank @Size(max = 255) String targetPath,
      boolean enabled,
      @Min(0) @Max(10000) int sortOrder,
      Instant startsAt,
      Instant endsAt,
      @NotNull @Min(0) Long expectedRevision) {}

  public record LegalCreate(
      @NotNull ConsentDocument document,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,50}") String documentVersion,
      @NotBlank @Size(max = 200) String title,
      @NotBlank @Size(max = 50000) String body) {}

  public record LegalEdit(
      @NotBlank @Size(max = 200) String title,
      @NotBlank @Size(max = 50000) String body,
      @NotNull @Min(0) Long expectedRevision) {}

  public record Publish(@NotNull @Min(0) Long expectedRevision) {}
}
