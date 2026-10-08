package com.lootfast.backend.content.repository;

import com.lootfast.backend.content.entity.LegalContent;
import com.lootfast.backend.user.entity.ConsentDocument;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface LegalContentRepository extends JpaRepository<LegalContent, UUID> {
  interface Summary {
    UUID getId();

    ConsentDocument getDocument();

    String getDocumentVersion();

    String getTitle();

    java.time.Instant getPublishedAt();

    long getRevision();
  }

  Page<Summary> findAllBy(Pageable page);

  @Query("select x from LegalContent x where x.id=:id")
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<LegalContent> lock(UUID id);

  @Query("select l.document from LegalContent l where l.id=:id")
  Optional<ConsentDocument> documentOf(UUID id);

  boolean existsByDocumentAndDocumentVersion(ConsentDocument document, String version);

  Optional<LegalContent> findByDocumentAndDocumentVersionAndPublishedAtIsNotNull(
      ConsentDocument document, String version);
}
