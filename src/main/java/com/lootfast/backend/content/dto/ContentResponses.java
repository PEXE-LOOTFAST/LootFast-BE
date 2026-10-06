package com.lootfast.backend.content.dto;

import com.lootfast.backend.content.entity.*;
import com.lootfast.backend.user.entity.ConsentDocument;
import java.time.Instant;
import java.util.UUID;

public final class ContentResponses {
  private ContentResponses() {}

  public record CategoryView(
      String code, String name, boolean enabled, int sortOrder, long revision) {
    public static CategoryView from(Category c) {
      return new CategoryView(
          c.getCode(), c.getName(), c.isEnabled(), c.getSortOrder(), c.getRevision());
    }
  }

  public record BannerView(
      UUID id,
      String title,
      String altText,
      String targetPath,
      String imageUrl,
      boolean enabled,
      int sortOrder,
      Instant startsAt,
      Instant endsAt,
      long revision) {
    public static BannerView from(Banner b) {
      return new BannerView(
          b.getId(),
          b.getTitle(),
          b.getAltText(),
          b.getTargetPath(),
          b.getImageUrl(),
          b.isEnabled(),
          b.getSortOrder(),
          b.getStartsAt(),
          b.getEndsAt(),
          b.getRevision());
    }
  }

  public record LegalView(
      UUID id,
      ConsentDocument document,
      String documentVersion,
      String title,
      String body,
      String format,
      Instant publishedAt,
      long revision) {
    public static LegalView from(LegalContent l) {
      return new LegalView(
          l.getId(),
          l.getDocument(),
          l.getDocumentVersion(),
          l.getTitle(),
          l.getBody(),
          "PLAIN_TEXT",
          l.getPublishedAt(),
          l.getRevision());
    }
  }

  public record LegalSummary(
      UUID id,
      ConsentDocument document,
      String documentVersion,
      String title,
      Instant publishedAt,
      long revision) {
    public static LegalSummary from(LegalContent l) {
      return new LegalSummary(
          l.getId(),
          l.getDocument(),
          l.getDocumentVersion(),
          l.getTitle(),
          l.getPublishedAt(),
          l.getRevision());
    }
  }
}
