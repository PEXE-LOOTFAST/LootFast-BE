package com.lootfast.backend.content.entity;

import com.lootfast.backend.user.entity.ConsentDocument;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "content_legal_heads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalHead {
  @Id
  @Enumerated(EnumType.STRING)
  @Column(length = 32)
  private ConsentDocument document;

  private UUID currentId;

  public void publish(UUID id) {
    currentId = id;
  }
}
