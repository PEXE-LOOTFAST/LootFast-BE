package com.lootfast.backend.premium.dto;

import com.lootfast.backend.user.entity.UserRole;

public enum PremiumBenefit {
  MERCHANT_ADVANCED_REPORTS(UserRole.MERCHANT),
  BUYER_SAVED_FILTERS(UserRole.BUYER);
  private final UserRole role;

  PremiumBenefit(UserRole role) {
    this.role = role;
  }

  public UserRole role() {
    return role;
  }
}
