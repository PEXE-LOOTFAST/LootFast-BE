package com.lootfast.backend.merchant.application.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.common.storage.ImageStorageException;
import com.lootfast.backend.common.storage.PrivateImageStorage;
import com.lootfast.backend.merchant.application.dto.LicenseDownloadResponse;
import com.lootfast.backend.merchant.application.dto.MerchantApplicationResponse;
import com.lootfast.backend.merchant.application.entity.MerchantApplication;
import com.lootfast.backend.merchant.application.entity.MerchantApplicationStatus;
import com.lootfast.backend.merchant.application.repository.MerchantApplicationRepository;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class MerchantApplicationReviewService {

    private static final Duration DOWNLOAD_URL_VALIDITY = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final MerchantApplicationRepository applicationRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final PrivateImageStorage privateImageStorage;

    public MerchantApplicationReviewService(
            UserRepository userRepository,
            MerchantApplicationRepository applicationRepository,
            MerchantProfileRepository merchantProfileRepository,
            PrivateImageStorage privateImageStorage
    ) {
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.merchantProfileRepository = merchantProfileRepository;
        this.privateImageStorage = privateImageStorage;
    }

    @Transactional(readOnly = true)
    public PagedResponse<MerchantApplicationResponse> list(
            String adminSubject,
            MerchantApplicationStatus status,
            int page,
            int size
    ) {
        requireAdmin(adminSubject);
        Page<MerchantApplicationResponse> applications = applicationRepository
                .findAllByStatus(
                        status,
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(Sort.Direction.DESC, "submittedAt")
                        )
                )
                .map(MerchantApplicationResponse::from);
        return PagedResponse.from(applications);
    }

    @Transactional(readOnly = true)
    public LicenseDownloadResponse createLicenseDownloadUrl(
            String adminSubject,
            UUID applicationId
    ) {
        requireAdmin(adminSubject);
        MerchantApplication application = applicationRepository.findByPublicId(applicationId)
                .orElseThrow(this::applicationNotFound);
        if (application.getLicenseStorageKey() == null
                || application.getLicenseFormat() == null) {
            throw new ApiException(
                    ErrorCode.MERCHANT_APPLICATION_NOT_PENDING,
                    "Business license upload has not completed"
            );
        }

        Instant expiresAt = Instant.now().plus(DOWNLOAD_URL_VALIDITY);
        try {
            String url = privateImageStorage.createDownloadUrl(
                    application.getLicenseStorageKey(),
                    application.getLicenseFormat(),
                    DOWNLOAD_URL_VALIDITY
            );
            return new LicenseDownloadResponse(url, expiresAt);
        } catch (ImageStorageException exception) {
            throw new ApiException(
                    ErrorCode.LICENSE_ACCESS_FAILED,
                    "Business license could not be opened at this time"
            );
        }
    }

    @Transactional
    public MerchantApplicationResponse approve(
            String adminSubject,
            UUID applicationId
    ) {
        User admin = requireAdmin(adminSubject);
        LockedApplication locked = lockApplicantThenApplication(applicationId);
        MerchantApplication application = locked.application();

        if (application.getStatus() == MerchantApplicationStatus.APPROVED) {
            ensureMerchantAccess(locked.applicant(), application.getReviewedAt());
            return MerchantApplicationResponse.from(application);
        }
        requirePending(application);
        if (admin.getId().equals(locked.applicant().getId())) {
            throw new ApiException(
                    ErrorCode.ACCESS_DENIED,
                    "An admin cannot approve their own merchant application"
            );
        }

        Instant approvedAt = Instant.now();
        ensureMerchantAccess(locked.applicant(), approvedAt);
        application.approve(admin, approvedAt);
        return MerchantApplicationResponse.from(application);
    }

    @Transactional
    public MerchantApplicationResponse reject(
            String adminSubject,
            UUID applicationId,
            String reason
    ) {
        User admin = requireAdmin(adminSubject);
        LockedApplication locked = lockApplicantThenApplication(applicationId);
        MerchantApplication application = locked.application();

        if (application.getStatus() == MerchantApplicationStatus.REJECTED) {
            return MerchantApplicationResponse.from(application);
        }
        requirePending(application);
        if (admin.getId().equals(locked.applicant().getId())) {
            throw new ApiException(
                    ErrorCode.ACCESS_DENIED,
                    "An admin cannot review their own merchant application"
            );
        }

        application.reject(admin, reason.trim(), Instant.now());
        return MerchantApplicationResponse.from(application);
    }

    private LockedApplication lockApplicantThenApplication(UUID applicationId) {
        Long applicantId = applicationRepository
                .findApplicantUserIdByPublicId(applicationId)
                .orElseThrow(this::applicationNotFound);
        User applicant = userRepository.findByIdForUpdate(applicantId)
                .orElseThrow(this::applicationNotFound);
        MerchantApplication application = applicationRepository
                .findByPublicIdForUpdate(applicationId)
                .orElseThrow(this::applicationNotFound);
        return new LockedApplication(applicant, application);
    }

    private void ensureMerchantAccess(User applicant, Instant approvedAt) {
        applicant.addRole(UserRole.MERCHANT);
        MerchantProfile profile = merchantProfileRepository.findByUserId(applicant.getId())
                .orElseGet(() -> new MerchantProfile(applicant));
        profile.markVerified(approvedAt);
        merchantProfileRepository.save(profile);
    }

    private User requireAdmin(String subject) {
        Long userId = parseUserId(subject);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.USER_NOT_FOUND,
                        "Authenticated user is no longer available"
                ));
        if (!user.hasRole(UserRole.ADMIN)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Access is denied");
        }
        return user;
    }

    private void requirePending(MerchantApplication application) {
        if (application.getStatus() != MerchantApplicationStatus.PENDING) {
            throw new ApiException(
                    ErrorCode.MERCHANT_APPLICATION_NOT_PENDING,
                    "Only a pending merchant application can be reviewed"
            );
        }
    }

    private ApiException applicationNotFound() {
        return new ApiException(
                ErrorCode.MERCHANT_APPLICATION_NOT_FOUND,
                "Merchant application was not found"
        );
    }

    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException | NullPointerException exception) {
            throw new ApiException(
                    ErrorCode.USER_NOT_FOUND,
                    "Authenticated user is no longer available"
            );
        }
    }

    private record LockedApplication(User applicant, MerchantApplication application) {
    }
}
