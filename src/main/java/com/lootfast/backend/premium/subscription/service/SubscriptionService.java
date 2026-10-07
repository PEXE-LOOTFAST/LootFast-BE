package com.lootfast.backend.premium.subscription.service;

import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.content.service.ContentAccess;
import com.lootfast.backend.merchant.application.entity.MerchantApplicationStatus;
import com.lootfast.backend.merchant.application.repository.MerchantApplicationRepository;
import com.lootfast.backend.premium.catalog.dto.PlanAudience;
import com.lootfast.backend.premium.catalog.repository.PlanCatalogRepository;
import com.lootfast.backend.premium.dto.PremiumBenefit;
import com.lootfast.backend.premium.subscription.dto.SubscriptionResponse;
import com.lootfast.backend.premium.subscription.dto.SubscriptionResponse.*;
import com.lootfast.backend.premium.subscription.repository.SubscriptionRepository;
import com.lootfast.backend.user.entity.*;
import com.lootfast.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class SubscriptionService {
  private final SubscriptionRepository subscriptions;
  private final UserRepository users;
  private final MerchantApplicationRepository applications;
  private final PlanCatalogRepository plans;
  private final RenewalPolicy renewal;
  private final Clock clock;

  public SubscriptionService(
      SubscriptionRepository subscriptions,
      UserRepository users,
      MerchantApplicationRepository applications,
      PlanCatalogRepository plans,
      RenewalPolicy renewal,
      Clock clock) {
    this.subscriptions = subscriptions;
    this.users = users;
    this.applications = applications;
    this.plans = plans;
    this.renewal = renewal;
    this.clock = clock;
  }

  private User user(String subject) {
    long id;
    try {
      id = Long.parseLong(subject);
    } catch (RuntimeException e) {
      throw new ApiException(ErrorCode.ACCESS_DENIED);
    }
    return users
        .findByIdWithRoles(id)
        .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
  }

  private boolean eligible(User u, PlanAudience audience) {
    return !u.isLocked()
        && u.getStatus() == UserStatus.VERIFIED
        && u.hasRole(audience.role())
        && (audience != PlanAudience.MERCHANT
            || applications
                .findByUserId(u.getId())
                .map(a -> a.getStatus() == MerchantApplicationStatus.APPROVED)
                .orElse(false));
  }

  // Internal primitive only. Future verified payment handler must call inside its transaction.
  // There is intentionally no controller that accepts a frontend payment-success flag.
  @Transactional(propagation = Propagation.MANDATORY, timeout = 5)
  public Period applyConfirmedPurchase(
      UUID userId, UUID purchaseReference, UUID planRevisionId, Instant confirmedAt) {
    if (userId == null
        || purchaseReference == null
        || planRevisionId == null
        || confirmedAt == null) throw new ApiException(ErrorCode.VALIDATION_FAILED);
    var target =
        users
            .findByPublicIdForUpdate(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS),
        confirmed = confirmedAt.truncatedTo(ChronoUnit.MICROS);
    var existing = subscriptions.byPurchase(purchaseReference);
    if (existing.isPresent())
      return same(existing.get(), target.getId(), planRevisionId, confirmed);
    if (confirmed.isAfter(now) || confirmed.isBefore(Instant.parse("0001-01-01T00:00:00Z")))
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid confirmation time");
    var plan =
        plans
            .get(planRevisionId, false)
            .orElseThrow(() -> new ApiException(ErrorCode.PREMIUM_PLAN_NOT_FOUND));
    if (plan.publishedAt() == null)
      throw new ApiException(
          ErrorCode.PREMIUM_PLAN_CONFLICT, "Cannot allocate an unpublished plan");
    if (!target.hasRole(plan.audience().role()))
      throw new ApiException(
          ErrorCode.ACCESS_DENIED, "Purchase audience does not match account role");
    // Account locks suspend access, not an already confirmed payment's recorded period.
    var subscription = subscriptions.ensure(target.getId(), plan.audience(), now);
    var window =
        renewal.allocate(
            now, subscriptions.paidEnd(target.getId(), plan.audience()), plan.durationDays());
    subscriptions.insert(subscription, purchaseReference, plan, confirmed, window, now);
    return same(
        subscriptions.byPurchase(purchaseReference).orElseThrow(),
        target.getId(),
        planRevisionId,
        confirmed);
  }

  private Period same(Period p, long user, UUID plan, Instant confirmed) {
    if (subscriptions.owner(p.purchaseReference()) != user
        || !p.planRevisionId().equals(plan)
        || !p.confirmedAt().equals(confirmed))
      throw new ApiException(
          ErrorCode.PREMIUM_SUBSCRIPTION_CONFLICT,
          "Purchase reference is already allocated to a different payload");
    return p;
  }

  private SubscriptionResponse summary(
      User u, PlanAudience audience, List<Coverage> coverage, Instant now) {
    boolean allowed = eligible(u, audience);
    var end = subscriptions.paidEnd(u.getId(), audience);
    String state =
        end == null ? "NONE" : !end.isAfter(now) ? "EXPIRED" : allowed ? "ACTIVE" : "SUSPENDED";
    var rights = new ArrayList<Benefit>();
    for (var b : PremiumBenefit.values())
      if (b.role() == audience.role()) {
        var c = coverage.stream().filter(v -> v.benefit() == b).findFirst();
        rights.add(
            new Benefit(
                b,
                allowed && c.isPresent(),
                c.map(Coverage::sources).orElse(Set.of()),
                c.map(Coverage::until).orElse(null)));
      }
    return new SubscriptionResponse(audience, state, allowed, end, List.copyOf(rights));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
  public List<SubscriptionResponse> list(String subject) {
    var u = user(subject);
    var now = clock.instant();
    var coverage = subscriptions.coverage(u.getId(), now);
    return Arrays.stream(PlanAudience.values()).map(a -> summary(u, a, coverage, now)).toList();
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
  public SubscriptionResponse benefits(String subject, PlanAudience audience) {
    if (audience == null) throw new ApiException(ErrorCode.VALIDATION_FAILED);
    var u = user(subject);
    var now = clock.instant();
    return summary(u, audience, subscriptions.coverage(u.getId(), now), now);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
  public PagedResponse<Period> periods(String subject, PlanAudience audience, int page, int size) {
    if (audience == null) throw new ApiException(ErrorCode.VALIDATION_FAILED);
    var u = user(subject);
    ContentAccess.page(page, size, "id");
    return subscriptions.periods(u.getId(), audience, page, size);
  }
}
