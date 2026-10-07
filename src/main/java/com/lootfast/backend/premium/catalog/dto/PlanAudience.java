package com.lootfast.backend.premium.catalog.dto;

import com.lootfast.backend.user.entity.UserRole;

public enum PlanAudience {
  BUYER,
  MERCHANT;

  public UserRole role() {
    return UserRole.valueOf(name());
  }
}
