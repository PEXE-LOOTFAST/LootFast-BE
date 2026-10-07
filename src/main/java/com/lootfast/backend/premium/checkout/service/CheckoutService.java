package com.lootfast.backend.premium.checkout.service;

import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.content.service.ContentAccess;
import com.lootfast.backend.merchant.application.entity.MerchantApplicationStatus;
import com.lootfast.backend.merchant.application.repository.MerchantApplicationRepository;
import com.lootfast.backend.premium.catalog.dto.PlanAudience;
import com.lootfast.backend.premium.catalog.repository.PlanCatalogRepository;
import com.lootfast.backend.premium.checkout.dto.CheckoutDtos.*;
import com.lootfast.backend.premium.checkout.repository.CheckoutRepository;
import com.lootfast.backend.user.entity.*;
import com.lootfast.backend.user.repository.UserRepository;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class CheckoutService {
  private final CheckoutRepository orders;
  private final PlanCatalogRepository plans;
  private final UserRepository users;
  private final MerchantApplicationRepository applications;
  private final VnpayCheckout gateway;
  private final Clock clock;

  public CheckoutService(
      CheckoutRepository orders,
      PlanCatalogRepository plans,
      UserRepository users,
      MerchantApplicationRepository applications,
      VnpayCheckout gateway,
      Clock clock) {
    this.orders = orders;
    this.plans = plans;
    this.users = users;
    this.applications = applications;
    this.gateway = gateway;
    this.clock = clock;
  }

  private User user(String subject, boolean lock) {
    long id;
    try {
      id = Long.parseLong(subject);
    } catch (RuntimeException e) {
      throw new ApiException(ErrorCode.ACCESS_DENIED);
    }
    return (lock ? users.findByIdForUpdate(id) : users.findByIdWithRoles(id))
        .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
  }

  private void eligible(User u, PlanAudience a) {
    if (u.isLocked()) throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
    if (u.getStatus() != UserStatus.VERIFIED)
      throw new ApiException(ErrorCode.ACCOUNT_NOT_VERIFIED);
    if (!u.hasRole(a.role())) throw new ApiException(ErrorCode.ACCESS_DENIED);
    if (a == PlanAudience.MERCHANT
        && !applications
            .findByUserId(u.getId())
            .map(x -> x.getStatus() == MerchantApplicationStatus.APPROVED)
            .orElse(false)) throw new ApiException(ErrorCode.MERCHANT_NOT_APPROVED);
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.SECONDS);
  }

  private Order status(Order o) {
    if (!o.status().equals("PENDING_PAYMENT") || o.expiresAt().isAfter(clock.instant())) return o;
    return new Order(
        o.id(),
        o.attempt() == null ? "EXPIRED" : "AWAITING_RECONCILIATION",
        o.plan(),
        o.acceptedAt(),
        o.createdAt(),
        o.expiresAt(),
        o.attempt());
  }

  @Transactional(timeout = 5)
  public Order create(String subject, UUID key, Create request) {
    if (key == null
        || request == null
        || request.planRevisionId() == null
        || request.termsDocumentId() == null
        || !request.acceptTerms())
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "Idempotency key and exact plan terms consent are required");
    var u = user(subject, true);
    var existing = orders.byKey(u.getId(), key);
    if (existing.isPresent()) {
      var o = existing.get();
      if (!o.plan().id().equals(request.planRevisionId())
          || !o.plan().termsDocumentId().equals(request.termsDocumentId()))
        throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT);
      eligible(u, o.plan().audience());
      return status(o);
    }
    plans.lockPlan(plans.codeOf(request.planRevisionId()));
    var plan =
        plans
            .get(request.planRevisionId(), true)
            .orElseThrow(() -> new ApiException(ErrorCode.PREMIUM_PLAN_NOT_FOUND));
    eligible(u, plan.audience());
    if (!plan.termsDocumentId().equals(request.termsDocumentId()))
      throw new ApiException(ErrorCode.CONSENT_VERSION_OUTDATED);
    var now = now();
    if (orders.pending(u.getId(), plan.audience(), now))
      throw new ApiException(
          ErrorCode.PREMIUM_CHECKOUT_CONFLICT,
          "An unexpired order already exists for this audience");
    if (orders.recentCount(u.getId(), now.minus(Duration.ofHours(1))) >= 10)
      throw new ApiException(
          ErrorCode.PAYMENT_RETRY_LATER, "At most 10 new Premium orders per hour");
    var id = orders.insert(u.getId(), key, plan, now, now.plus(Duration.ofMinutes(15)));
    return orders.owned(u.getId(), id).orElseThrow();
  }

  @Transactional(timeout = 5)
  public Payment pay(String subject, UUID id, String ip) {
    var u = user(subject, true);
    orders.lock(u.getId(), id);
    var o =
        orders
            .owned(u.getId(), id)
            .orElseThrow(() -> new ApiException(ErrorCode.PREMIUM_ORDER_NOT_FOUND));
    eligible(u, o.plan().audience());
    gateway.requireEnabled();
    if (!o.status().equals("PENDING_PAYMENT") || !o.expiresAt().isAfter(now()))
      throw new ApiException(
          ErrorCode.PAYMENT_STATE_CONFLICT,
          "Order is no longer open; expired payment needs reconciliation");
    if (o.attempt() != null)
      return new Payment(o.id(), o.attempt(), orders.paymentUrl(o.attempt().id()));
    var now = now();
    var ref = UUID.randomUUID().toString().replace("-", "");
    var url = gateway.url(ref, o.plan().priceVnd(), now, o.expiresAt(), ip);
    var attempt = orders.attempt(o.id(), ref, now, o.expiresAt(), url);
    return new Payment(o.id(), attempt, url);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
  public Order get(String subject, UUID id) {
    var u = user(subject, false);
    return status(
        orders
            .owned(u.getId(), id)
            .orElseThrow(() -> new ApiException(ErrorCode.PREMIUM_ORDER_NOT_FOUND)));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
  public PagedResponse<Order> list(String subject, int page, int size) {
    ContentAccess.page(page, size, "id");
    var u = user(subject, false);
    var p = orders.list(u.getId(), page, size);
    return new PagedResponse<>(
        p.items().stream().map(this::status).toList(),
        p.page(),
        p.size(),
        p.totalElements(),
        p.totalPages());
  }
}
