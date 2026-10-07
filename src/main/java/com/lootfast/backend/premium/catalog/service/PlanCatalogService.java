package com.lootfast.backend.premium.catalog.service;

import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.content.repository.LegalContentRepository;
import com.lootfast.backend.content.service.ContentAccess;
import com.lootfast.backend.premium.catalog.dto.*;
import com.lootfast.backend.premium.catalog.dto.PlanRequests.*;
import com.lootfast.backend.premium.catalog.repository.PlanCatalogRepository;
import com.lootfast.backend.user.entity.ConsentDocument;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class PlanCatalogService {
  private final PlanCatalogRepository plans;
  private final ContentAccess access;
  private final LegalContentRepository legal;
  private final Clock clock;

  public PlanCatalogService(
      PlanCatalogRepository plans,
      ContentAccess access,
      LegalContentRepository legal,
      Clock clock) {
    this.plans = plans;
    this.access = access;
    this.legal = legal;
    this.clock = clock;
  }

  private PlanResponse required(UUID id) {
    return plans
        .get(id, false)
        .orElseThrow(() -> new ApiException(ErrorCode.PREMIUM_PLAN_NOT_FOUND));
  }

  private void configuration(PlanAudience audience, Configuration c) {
    if (c == null
        || c.name() == null
        || c.name().isBlank()
        || c.name().length() > 120
        || c.description() == null
        || c.description().isBlank()
        || c.description().length() > 2000
        || c.priceVnd() == null
        || c.priceVnd() < 1000
        || c.priceVnd() > 999999999
        || c.durationDays() == null
        || c.durationDays() < 1
        || c.durationDays() > 366
        || c.termsDocumentId() == null
        || c.benefits() == null
        || c.benefits().isEmpty()
        || c.benefits().size() > 8
        || c.benefits().stream().anyMatch(v -> v == null || v.role() != audience.role()))
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          "Provide a valid configuration with supported benefits for this audience");
    var terms =
        legal
            .findById(c.termsDocumentId())
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.VALIDATION_FAILED, "Select an existing terms document"));
    if (terms.getDocument() != ConsentDocument.TERMS_OF_USE)
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "Select TERMS_OF_USE");
  }

  private void expected(PlanResponse r, Long expected) {
    if (expected == null || expected != r.revision())
      throw new ApiException(
          ErrorCode.PREMIUM_PLAN_CONFLICT, "Plan changed; reload before editing");
  }

  private void reason(Change c) {
    if (c.reason() == null || c.reason().isBlank() || c.reason().length() > 500)
      throw new ApiException(ErrorCode.VALIDATION_FAILED);
  }

  @Transactional(timeout = 5)
  public PlanResponse create(String subject, Create r) {
    var actor = access.admin(subject);
    if (r.code() == null || !r.code().matches("[A-Z0-9_]{1,64}") || r.audience() == null)
      throw new ApiException(ErrorCode.VALIDATION_FAILED);
    configuration(r.audience(), r.configuration());
    var now = clock.instant();
    var p = plans.createPlan(r.code(), r.audience(), actor.getPublicId(), now);
    var result = required(plans.draft(p, r.configuration(), actor.getPublicId(), now));
    plans.audit(result, actor.getPublicId(), "CREATE", "Create draft", now);
    return result;
  }

  @Transactional(timeout = 5)
  public PlanResponse draft(String subject, String code, Configuration c) {
    var actor = access.admin(subject);
    var p = plans.lockPlan(code);
    configuration(plans.audience(p), c);
    var now = clock.instant();
    var r = required(plans.draft(p, c, actor.getPublicId(), now));
    plans.audit(r, actor.getPublicId(), "CREATE", "Create new revision", now);
    return r;
  }

  @Transactional(timeout = 5)
  public PlanResponse edit(String subject, UUID id, Edit c) {
    var actor = access.admin(subject);
    plans.lockPlan(plans.codeOf(id));
    var r = required(id);
    expected(r, c.expectedRevision());
    if (!r.status().equals("DRAFT"))
      throw new ApiException(
          ErrorCode.PREMIUM_PLAN_CONFLICT, "Only draft configuration can be edited");
    configuration(r.audience(), c.configuration());
    var now = clock.instant();
    plans.edit(id, c.configuration(), actor.getPublicId(), now);
    var result = required(id);
    plans.audit(result, actor.getPublicId(), "EDIT", "Update draft", now);
    return result;
  }

  @Transactional(timeout = 5)
  public PlanResponse change(String subject, UUID id, Change c, boolean publish) {
    var actor = access.admin(subject);
    plans.lockPlan(plans.codeOf(id));
    var r = required(id);
    expected(r, c.expectedRevision());
    reason(c);
    if (publish && !r.status().equals("DRAFT"))
      throw new ApiException(ErrorCode.PREMIUM_PLAN_CONFLICT, "Only drafts can be published");
    if (!publish && r.status().equals("ARCHIVED")) return r;
    var now = clock.instant();
    if (publish) {
      var terms =
          legal
              .findById(r.termsDocumentId())
              .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED));
      if (terms.getPublishedAt() == null)
        throw new ApiException(
            ErrorCode.VALIDATION_FAILED, "Publish terms before publishing the plan");
      var previous = plans.published(r.code());
      if (previous.isPresent()) {
        plans.state(previous.get().id(), "ARCHIVED", actor.getPublicId(), now);
        plans.audit(
            required(previous.get().id()),
            actor.getPublicId(),
            "AUTO_ARCHIVE",
            c.reason().trim(),
            now);
      }
    }
    plans.state(id, publish ? "PUBLISHED" : "ARCHIVED", actor.getPublicId(), now);
    var result = required(id);
    plans.audit(
        result, actor.getPublicId(), publish ? "PUBLISH" : "ARCHIVE", c.reason().trim(), now);
    return result;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
  public PagedResponse<PlanResponse> list(
      String subject, boolean admin, PlanAudience audience, String code, int page, int size) {
    if (admin) access.admin(subject);
    ContentAccess.page(page, size, "id");
    if (code != null && !code.matches("[A-Z0-9_]{1,64}"))
      throw new ApiException(ErrorCode.VALIDATION_FAILED);
    return plans.list(admin, audience, code, page, size);
  }

  @Transactional(readOnly = true, timeout = 5)
  public PlanResponse get(String subject, UUID id, boolean admin) {
    if (admin) access.admin(subject);
    return plans
        .get(id, !admin)
        .orElseThrow(() -> new ApiException(ErrorCode.PREMIUM_PLAN_NOT_FOUND));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
  public PagedResponse<PlanAuditResponse> history(String subject, String code, int page, int size) {
    access.admin(subject);
    ContentAccess.page(page, size, "id");
    // Read lookup avoids locking a plan in a read-only transaction.
    if (plans.list(true, null, code, 0, 1).totalElements() == 0)
      throw new ApiException(ErrorCode.PREMIUM_PLAN_NOT_FOUND);
    return plans.history(code, page, size);
  }
}
