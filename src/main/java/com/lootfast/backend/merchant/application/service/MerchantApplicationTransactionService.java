package com.lootfast.backend.merchant.application.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.merchant.application.dto.MerchantApplicationResponse;
import com.lootfast.backend.merchant.application.entity.MerchantApplication;
import com.lootfast.backend.merchant.application.entity.MerchantApplicationStatus;
import com.lootfast.backend.merchant.application.repository.MerchantApplicationRepository;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class MerchantApplicationTransactionService {

    private static final Duration STALE_UPLOAD_AFTER = Duration.ofMinutes(10);

    private final UserRepository userRepository;
    private final MerchantApplicationRepository applicationRepository;

    public MerchantApplicationTransactionService(
            UserRepository userRepository,
            MerchantApplicationRepository applicationRepository
    ) {
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
    }

    @Transactional
    public UploadAttempt beginSubmission(String subject, String licenseNumber) {
        Long userId = parseUserId(subject);
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(this::userNotFound);

        if (user.getStatus() != UserStatus.VERIFIED || !user.hasRole(UserRole.BUYER)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Access is denied");
        }
        if (user.hasRole(UserRole.MERCHANT)) {
            throw new ApiException(
                    ErrorCode.MERCHANT_ALREADY_APPROVED,
                    "This account already has merchant access"
            );
        }

        UUID submissionId = UUID.randomUUID();
        MerchantApplication application = applicationRepository
                .findByUserIdForUpdate(userId)
                .orElse(null);

        if (application == null) {
            application = applicationRepository.save(
                    new MerchantApplication(user, licenseNumber, submissionId)
            );
        } else {
            ensureCanStartUpload(application);
            application.startUpload(licenseNumber, submissionId);
        }

        return new UploadAttempt(user.getPublicId(), submissionId);
    }

    @Transactional
    public MerchantApplicationResponse finishSubmission(
            String subject,
            UUID submissionId,
            String storageKey,
            String format
    ) {
        Long userId = parseUserId(subject);
        MerchantApplication application = applicationRepository
                .findByUserIdForUpdate(userId)
                .orElseThrow(this::applicationNotFound);
        requireCurrentUpload(application, submissionId);
        application.markPending(storageKey, format, Instant.now());
        return MerchantApplicationResponse.from(application);
    }

    @Transactional
    public void markUploadFailed(String subject, UUID submissionId) {
        Long userId = parseUserId(subject);
        applicationRepository.findByUserIdForUpdate(userId)
                .filter(application -> application.getStatus()
                        == MerchantApplicationStatus.UPLOADING)
                .filter(application -> application.getSubmissionId().equals(submissionId))
                .ifPresent(MerchantApplication::markUploadFailed);
    }

    @Transactional(readOnly = true)
    public MerchantApplicationResponse getCurrent(String subject) {
        Long userId = parseUserId(subject);
        MerchantApplication application = applicationRepository.findByUserId(userId)
                .orElseThrow(this::applicationNotFound);
        return MerchantApplicationResponse.from(application);
    }

    private void ensureCanStartUpload(MerchantApplication application) {
        if (application.getStatus() == MerchantApplicationStatus.APPROVED) {
            throw new ApiException(
                    ErrorCode.MERCHANT_ALREADY_APPROVED,
                    "This account already has merchant access"
            );
        }
        if (application.getStatus() == MerchantApplicationStatus.PENDING) {
            throw alreadyPending();
        }
        if (application.getStatus() == MerchantApplicationStatus.UPLOADING
                && application.getUpdatedAt().isAfter(
                Instant.now().minus(STALE_UPLOAD_AFTER))) {
            throw alreadyPending();
        }
    }

    private void requireCurrentUpload(
            MerchantApplication application,
            UUID submissionId
    ) {
        if (application.getStatus() != MerchantApplicationStatus.UPLOADING
                || !application.getSubmissionId().equals(submissionId)) {
            throw alreadyPending();
        }
    }

    private ApiException alreadyPending() {
        return new ApiException(
                ErrorCode.MERCHANT_APPLICATION_ALREADY_PENDING,
                "A merchant application is already being uploaded or waiting for review"
        );
    }

    private ApiException applicationNotFound() {
        return new ApiException(
                ErrorCode.MERCHANT_APPLICATION_NOT_FOUND,
                "Merchant application was not found"
        );
    }

    private ApiException userNotFound() {
        return new ApiException(
                ErrorCode.USER_NOT_FOUND,
                "Authenticated user is no longer available"
        );
    }

    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException | NullPointerException exception) {
            throw userNotFound();
        }
    }

    public record UploadAttempt(UUID userPublicId, UUID submissionId) {
    }
}
