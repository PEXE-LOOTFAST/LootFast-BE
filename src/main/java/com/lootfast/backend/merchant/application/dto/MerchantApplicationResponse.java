package com.lootfast.backend.merchant.application.dto;

import com.lootfast.backend.merchant.application.entity.MerchantApplication;
import com.lootfast.backend.merchant.application.entity.MerchantApplicationStatus;
import com.lootfast.backend.user.entity.UserRole;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record MerchantApplicationResponse(
        UUID applicationId,
        UUID applicantId,
        String applicantEmail,
        String applicantName,
        Set<UserRole> userRoles,
        String businessLicenseNumber,
        MerchantApplicationStatus status,
        String rejectionReason,
        Instant submittedAt,
        Instant reviewedAt,
        UUID reviewedByAdminId
) {
    public static MerchantApplicationResponse from(MerchantApplication application) {
        return new MerchantApplicationResponse(
                application.getPublicId(),
                application.getUser().getPublicId(),
                application.getUser().getEmail(),
                application.getUser().getFirstName() + " " + application.getUser().getLastName(),
                application.getUser().getRoles(),
                application.getBusinessLicenseNumber(),
                application.getStatus(),
                application.getRejectionReason(),
                application.getSubmittedAt(),
                application.getReviewedAt(),
                application.getReviewedBy() == null
                        ? null
                        : application.getReviewedBy().getPublicId()
        );
    }
}
