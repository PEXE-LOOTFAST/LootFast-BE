package com.lootfast.backend.content.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "content_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {
  @Id
  @Column(length = 100)
  private String code;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(nullable = false)
  private boolean enabled;

  @Column(nullable = false)
  private int sortOrder;

  @Version private Long revision;

  @Column(nullable = false)
  private Instant updatedAt;

  private UUID updatedBy;

  public Category(String code) {
    this.code = code;
  }

  public void configure(String name, boolean enabled, int sortOrder, Instant now, UUID actor) {
    this.name = name;
    this.enabled = enabled;
    this.sortOrder = sortOrder;
    updatedAt = now;
    updatedBy = actor;
  }
}
