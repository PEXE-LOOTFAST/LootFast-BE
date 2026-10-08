package com.lootfast.backend.content.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "content_banners")
@Getter
public class Banner {
  @Id private UUID id;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 200)
  private String altText;

  @Column(nullable = false, length = 255)
  private String targetPath;

  @Column(length = 2048)
  private String imageUrl;

  @Column(length = 255)
  private String storageKey;

  @Column(nullable = false)
  private boolean enabled;

  @Column(nullable = false)
  private int sortOrder;

  private Instant startsAt;
  private Instant endsAt;
  @Version private Long revision;

  @Column(nullable = false)
  private Instant updatedAt;

  @Column(nullable = false)
  private UUID updatedBy;

  public Banner() {
    id = UUID.randomUUID();
  }

  public void configure(
      String title,
      String altText,
      String targetPath,
      boolean enabled,
      int order,
      Instant start,
      Instant end,
      Instant now,
      UUID actor) {
    this.title = title;
    this.altText = altText;
    this.targetPath = targetPath;
    this.enabled = enabled;
    sortOrder = order;
    startsAt = start;
    endsAt = end;
    updatedAt = now;
    updatedBy = actor;
  }

  public void image(String url, String key, Instant now, UUID actor) {
    imageUrl = url;
    storageKey = key;
    updatedAt = now;
    updatedBy = actor;
  }
}
