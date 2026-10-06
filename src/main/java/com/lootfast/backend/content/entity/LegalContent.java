package com.lootfast.backend.content.entity;

import com.lootfast.backend.user.entity.ConsentDocument;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "content_legal_versions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"document", "document_version"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalContent {
  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ConsentDocument document;

  @Column(name = "document_version", nullable = false, length = 50)
  private String documentVersion;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String body;

  private Instant publishedAt;
  @Version private Long revision;

  @Column(nullable = false)
  private Instant updatedAt;

  @Column(nullable = false)
  private UUID updatedBy;

  public LegalContent(ConsentDocument document, String version) {
    id = UUID.randomUUID();
    this.document = document;
    documentVersion = version;
  }

  public void edit(String title, String body, Instant now, UUID actor) {
    this.title = title;
    this.body = body;
    updatedAt = now;
    updatedBy = actor;
  }

  public void publish(Instant now, UUID actor) {
    publishedAt = now;
    updatedAt = now;
    updatedBy = actor;
  }
}
