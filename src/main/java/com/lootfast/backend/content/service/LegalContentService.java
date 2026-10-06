package com.lootfast.backend.content.service;

import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.content.dto.ContentRequests.*;
import com.lootfast.backend.content.dto.ContentResponses.*;
import com.lootfast.backend.content.entity.*;
import com.lootfast.backend.content.repository.*;
import com.lootfast.backend.user.entity.ConsentDocument;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LegalContentService {
  private final LegalContentRepository versions;
  private final LegalHeadRepository heads;
  private final ContentAccess access;
  private final Clock clock;

  public LegalContentService(
      LegalContentRepository versions,
      LegalHeadRepository heads,
      ContentAccess access,
      Clock clock) {
    this.versions = versions;
    this.heads = heads;
    this.access = access;
    this.clock = clock;
  }

  @Transactional(readOnly = true, timeout = 5)
  public PagedResponse<LegalSummary> list(String subject, int page, int size) {
    access.admin(subject);
    return PagedResponse.from(
        versions
            .findAllBy(ContentAccess.page(page, size, "document", "documentVersion"))
            .map(
                l ->
                    new LegalSummary(
                        l.getId(),
                        l.getDocument(),
                        l.getDocumentVersion(),
                        l.getTitle(),
                        l.getPublishedAt(),
                        l.getRevision())));
  }

  @Transactional(readOnly = true, timeout = 5)
  public LegalView get(String subject, UUID id) {
    access.admin(subject);
    return LegalView.from(versions.findById(id).orElseThrow(ContentAccess::missing));
  }

  @Transactional(timeout = 5)
  public LegalView create(String subject, LegalCreate r) {
    var actor = access.admin(subject);
    // Serialize version creation/publication for each document, including duplicate version
    // requests.
    heads.lock(r.document()).orElseThrow(ContentAccess::missing);
    if (versions.existsByDocumentAndDocumentVersion(r.document(), r.documentVersion()))
      throw new ApiException(ErrorCode.CONTENT_CONFLICT, "Document version already exists");
    LegalContent l = new LegalContent(r.document(), r.documentVersion());
    l.edit(r.title().trim(), r.body().trim(), clock.instant(), actor.getPublicId());
    return LegalView.from(versions.saveAndFlush(l));
  }

  @Transactional(timeout = 5)
  public LegalView edit(String subject, UUID id, LegalEdit r) {
    var actor = access.admin(subject);
    LegalContent l = versions.lock(id).orElseThrow(ContentAccess::missing);
    ContentAccess.revision(l.getRevision(), r.expectedRevision());
    if (l.getPublishedAt() != null) throw immutable();
    l.edit(r.title().trim(), r.body().trim(), clock.instant(), actor.getPublicId());
    versions.flush();
    return LegalView.from(l);
  }

  @Transactional(timeout = 5)
  public LegalView publish(String subject, UUID id, long revision) {
    var actor = access.admin(subject);
    var document = versions.documentOf(id).orElseThrow(ContentAccess::missing);
    LegalHead h = heads.lock(document).orElseThrow(ContentAccess::missing);
    LegalContent l = versions.lock(id).orElseThrow(ContentAccess::missing);
    ContentAccess.revision(l.getRevision(), revision);
    if (l.getPublishedAt() != null) throw immutable();
    l.publish(clock.instant(), actor.getPublicId());
    h.publish(l.getId());
    versions.flush();
    return LegalView.from(l);
  }

  @Transactional(readOnly = true, timeout = 5)
  public LegalView current(ConsentDocument document) {
    var head = heads.findById(document).orElseThrow(ContentAccess::missing);
    if (head.getCurrentId() == null)
      throw new ApiException(ErrorCode.CONTENT_NOT_FOUND, "No published document yet");
    return LegalView.from(
        versions.findById(head.getCurrentId()).orElseThrow(ContentAccess::missing));
  }

  @Transactional(readOnly = true, timeout = 5)
  public LegalView published(ConsentDocument document, String version) {
    return LegalView.from(
        versions
            .findByDocumentAndDocumentVersionAndPublishedAtIsNotNull(document, version)
            .orElseThrow(ContentAccess::missing));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void requireCurrentConsent(ConsentDocument document, String version) {
    var head = heads.lockReadable(document).orElseThrow(ContentAccess::missing);
    // Transitional compatibility: legacy signup continues until the first document is published.
    if (head.getCurrentId() == null) return;
    var current = versions.findById(head.getCurrentId()).orElseThrow(ContentAccess::missing);
    if (!current.getDocumentVersion().equals(version.trim()))
      throw new ApiException(
          ErrorCode.CONSENT_VERSION_OUTDATED,
          "Reload the current legal document and accept its version");
  }

  private ApiException immutable() {
    return new ApiException(
        ErrorCode.CONTENT_CONFLICT, "Published documents are immutable; create a new version");
  }
}
