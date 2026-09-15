package com.lootfast.backend.auth.service;

import com.lootfast.backend.auth.dto.OnboardingAction;
import com.lootfast.backend.user.entity.AccountType;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import org.springframework.stereotype.Service;

@Service
public class OnboardingService {

    public OnboardingAction nextAction(User user) {
        if (user.hasRole(UserRole.MERCHANT)) {
            return OnboardingAction.MERCHANT_DASHBOARD;
        }
        if (user.getAccountType() == AccountType.MERCHANT) {
            return OnboardingAction.MERCHANT_APPLICATION;
        }
        return OnboardingAction.BUYER_HOME;
    }
}
