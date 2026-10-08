package com.lootfast.backend.buyer.savedfilter.service;

import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.deal.service.MerchantDealAccess;
import com.lootfast.backend.premium.dto.PremiumBenefit;
import com.lootfast.backend.premium.repository.PremiumEntitlementRepository;
import com.lootfast.backend.user.entity.*;
import com.lootfast.backend.user.repository.UserRepository;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
public class BuyerPremiumAccess {
  private final UserRepository users;
  private final MerchantDealAccess subjects;
  private final PremiumEntitlementRepository grants;
  private final Clock clock;

  public BuyerPremiumAccess(
      UserRepository users,
      MerchantDealAccess subjects,
      PremiumEntitlementRepository grants,
      Clock clock) {
    this.users = users;
    this.subjects = subjects;
    this.grants = grants;
    this.clock = clock;
  }

  public User user(String subject, boolean lock) {
    Long id = subjects.parseUserId(subject);
    var u =
        (lock ? users.findByIdForUpdate(id) : users.findByIdWithRoles(id))
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    if (u.isLocked()) throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
    if (!u.hasRole(UserRole.BUYER)) throw new ApiException(ErrorCode.ACCESS_DENIED);
    if (u.getStatus() != UserStatus.VERIFIED)
      throw new ApiException(ErrorCode.ACCOUNT_NOT_VERIFIED);
    return u;
  }

  public boolean active(User u) {
    return grants.active(u.getId(), PremiumBenefit.BUYER_SAVED_FILTERS, clock.instant());
  }

  public void require(User u) {
    if (!active(u))
      throw new ApiException(
          ErrorCode.PREMIUM_REQUIRED, "An active buyer saved filters entitlement is required");
  }
}
