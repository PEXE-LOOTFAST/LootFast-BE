package com.lootfast.backend.auth.dto;

import com.lootfast.backend.user.entity.AccountType;

public record SignupResponse(
        String message,
        String email,
        AccountType accountType,
        OnboardingAction nextAction
) {
}
