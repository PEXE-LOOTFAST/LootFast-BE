package com.lootfast.backend.premium.service;

import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.deal.service.MerchantDealAccess;
import com.lootfast.backend.premium.dto.*;
import com.lootfast.backend.premium.repository.PremiumEntitlementRepository;
import com.lootfast.backend.user.entity.*;
import com.lootfast.backend.user.repository.UserRepository;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class PremiumEntitlementService {
  private final PremiumEntitlementRepository grants;
  private final UserRepository users;
  private final MerchantDealAccess access;
  private final Clock clock;

  public PremiumEntitlementService(
      PremiumEntitlementRepository grants,
      UserRepository users,
      MerchantDealAccess access,
      Clock clock) {
    this.grants = grants;
    this.users = users;
    this.access = access;
    this.clock = clock;
  }

  private User admin(String subject) {
    var a = access.requireAdmin(subject);
    if (a.isLocked() || a.getStatus() != UserStatus.VERIFIED)
      throw new ApiException(ErrorCode.ACCESS_DENIED);
    return a;
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public void requireAdvancedReports(User user) {
    if (user.isLocked()) throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
    if (!grants.active(user.getId(), clock.instant()))
      throw new ApiException(
          ErrorCode.PREMIUM_REQUIRED, "An active advanced reports entitlement is required");
  }

  @Transactional(readOnly = true, timeout = 5)
  public User reportUser(String subject) {
    var user = access.requireApprovedMerchant(subject);
    requireAdvancedReports(user);
    return user;
  }

  @Transactional(readOnly = true, timeout = 5)
  public boolean available(String subject) {
    var u = access.requireApprovedMerchant(subject);
    return !u.isLocked() && grants.active(u.getId(), clock.instant());
  }

  @Transactional(timeout = 5)
  public PremiumEntitlement grant(String subject, GrantPremiumRequest r) {
    var a = admin(subject);
    var now = clock.instant();
    if (r.id() == null || r.userId() == null) throw new ApiException(ErrorCode.VALIDATION_FAILED);
    if (r.startsAt() != null && r.endsAt() != null)
      r =
          new GrantPremiumRequest(
              r.id(),
              r.userId(),
              r.startsAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
              r.endsAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
              r.reason(),
              r.benefit());
    if (r.reason() == null
        || r.reason().isBlank()
        || r.reason().length() > 500
        || r.startsAt() == null
        || r.endsAt() == null
        || !r.startsAt().isBefore(r.endsAt())
        || Duration.between(r.startsAt(), r.endsAt()).compareTo(Duration.ofDays(366)) > 0
        || r.startsAt().isBefore(Instant.parse("0001-01-01T00:00:00Z"))
        || r.endsAt().isAfter(Instant.parse("9999-12-31T23:59:59.999999Z")))
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          "Provide a valid entitlement window of at most 366 days and a reason");
    var target =
        users
            .findByPublicIdForUpdate(r.userId())
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    if (!target.hasRole(r.benefit().role())
        || target.getStatus() != UserStatus.VERIFIED
        || target.isLocked())
      throw new ApiException(
          ErrorCode.ACCESS_DENIED,
          "Target must be verified, unlocked and have the role required by the benefit");
    var existing = grants.get(r.id());
    if (existing.isEmpty()) {
      if (!r.endsAt().isAfter(now))
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "New grants must end in the future");
      grants.insert(target.getId(), a.getPublicId(), r, now);
    }
    var result =
        grants.get(r.id()).orElseThrow(() -> new IllegalStateException("Grant was not persisted"));
    if (!result.source().equals("MANUAL")
        || !result.benefit().equals(r.benefit().name())
        || !result.userId().equals(r.userId())
        || !result.startsAt().equals(r.startsAt())
        || !result.endsAt().equals(r.endsAt())
        || !result.grantReason().equals(r.reason().trim())
        || !result.grantedBy().equals(a.getPublicId()))
      throw new ApiException(
          ErrorCode.PREMIUM_ENTITLEMENT_CONFLICT,
          "Grant id has already been used for a different request");
    return result;
  }

  @Transactional(timeout = 5)
  public PremiumEntitlement revoke(String subject, UUID id, String reason) {
    var a = admin(subject);
    if (reason == null || reason.isBlank() || reason.length() > 500)
      throw new ApiException(ErrorCode.VALIDATION_FAILED);
    var readable =
        grants.get(id).orElseThrow(() -> new ApiException(ErrorCode.PREMIUM_ENTITLEMENT_NOT_FOUND));
    if (!readable.source().equals("MANUAL"))
      throw new ApiException(
          ErrorCode.PREMIUM_SUBSCRIPTION_CONFLICT,
          "Paid rights require the paid subscription resolution flow");
    users
        .findByPublicIdForUpdate(readable.userId())
        .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    grants.revoke(id, a.getPublicId(), reason.trim(), clock.instant());
    return grants.get(id).orElseThrow();
  }

  @Transactional(readOnly = true, timeout = 5)
  public PagedResponse<PremiumEntitlement> list(String subject, UUID user, int page, int size) {
    admin(subject);
    if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE)
      throw new ApiException(ErrorCode.VALIDATION_FAILED);
    var target =
        users.findByPublicId(user).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    return grants.list(target.getId(), page, size);
  }
}
