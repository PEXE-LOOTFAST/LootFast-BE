package com.lootfast.backend.deal.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.merchant.application.entity.MerchantApplicationStatus;
import com.lootfast.backend.merchant.application.repository.MerchantApplicationRepository;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class MerchantDealAccess {

    private final UserRepository userRepository;
    private final MerchantApplicationRepository applicationRepository;

    public MerchantDealAccess(
            UserRepository userRepository,
            MerchantApplicationRepository applicationRepository
    ) {
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
    }

    public User requireApprovedMerchant(String subject) {
        Long userId = parseUserId(subject);
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.USER_NOT_FOUND,
                        "Authenticated user is no longer available"
                ));
        if (!user.hasRole(UserRole.MERCHANT)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Merchant role is required");
        }
        if (user.getStatus() != UserStatus.VERIFIED) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_VERIFIED, "Account must be verified");
        }
        boolean approved = applicationRepository.findByUserId(userId)
                .map(application -> application.getStatus() == MerchantApplicationStatus.APPROVED)
                .orElse(false);
        if (!approved) {
            throw new ApiException(
                    ErrorCode.MERCHANT_NOT_APPROVED,
                    "Merchant application must be approved before managing deals"
            );
        }
        return user;
    }

    public User requireAdmin(String subject) {
        Long userId = parseUserId(subject);
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.USER_NOT_FOUND,
                        "Authenticated user is no longer available"
                ));
        if (!user.hasRole(UserRole.ADMIN)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Admin role is required");
        }
        return user;
    }

    public Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException | NullPointerException exception) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND, "Authenticated user is unavailable");
        }
    }
}
