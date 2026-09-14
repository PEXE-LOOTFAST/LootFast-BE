package com.lootfast.backend.merchant.application.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.storage.ImageFileValidator;
import com.lootfast.backend.common.storage.ImageStorageException;
import com.lootfast.backend.common.storage.ImageValidationException;
import com.lootfast.backend.common.storage.ImageValidationFailure;
import com.lootfast.backend.common.storage.PrivateImageStorage;
import com.lootfast.backend.common.storage.StoredPrivateImage;
import com.lootfast.backend.merchant.application.dto.MerchantApplicationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.regex.Pattern;

@Service
public class MerchantApplicationSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(
            MerchantApplicationSubmissionService.class
    );
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_PIXEL_COUNT = 25_000_000L;
    private static final Pattern LICENSE_NUMBER_PATTERN = Pattern.compile("^[0-9]{10,14}$");

    private final ImageFileValidator imageFileValidator;
    private final PrivateImageStorage privateImageStorage;
    private final MerchantApplicationTransactionService transactionService;

    public MerchantApplicationSubmissionService(
            ImageFileValidator imageFileValidator,
            PrivateImageStorage privateImageStorage,
            MerchantApplicationTransactionService transactionService
    ) {
        this.imageFileValidator = imageFileValidator;
        this.privateImageStorage = privateImageStorage;
        this.transactionService = transactionService;
    }

    public MerchantApplicationResponse submit(
            String subject,
            String businessLicenseNumber,
            MultipartFile file
    ) {
        String normalizedLicenseNumber = normalizeLicenseNumber(businessLicenseNumber);
        byte[] content = validateAndRead(file);
        MerchantApplicationTransactionService.UploadAttempt attempt =
                transactionService.beginSubmission(subject, normalizedLicenseNumber);
        deletePreviousLicenseOrFail(subject, attempt);
        String storageKey = "lootfast/merchant-applications/"
                + attempt.userPublicId()
                + "/"
                + attempt.submissionId()
                + "/business-license";

        StoredPrivateImage storedImage;
        try {
            storedImage = privateImageStorage.uploadPrivate(storageKey, content);
        } catch (ImageStorageException exception) {
            markUploadFailedWithoutMaskingOriginalError(subject, attempt.submissionId());
            throw new ApiException(
                    ErrorCode.LICENSE_UPLOAD_FAILED,
                    "Business license could not be uploaded at this time"
            );
        }

        try {
            return transactionService.finishSubmission(
                    subject,
                    attempt.submissionId(),
                    storedImage.storageKey(),
                    storedImage.format()
            );
        } catch (RuntimeException exception) {
            deleteWithoutMaskingOriginalError(storedImage.storageKey());
            markUploadFailedWithoutMaskingOriginalError(subject, attempt.submissionId());
            throw exception;
        }
    }

    public MerchantApplicationResponse getCurrent(String subject) {
        return transactionService.getCurrent(subject);
    }

    private String normalizeLicenseNumber(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!LICENSE_NUMBER_PATTERN.matcher(normalized).matches()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "Business license number must contain 10 to 14 digits"
            );
        }
        return normalized;
    }

    private byte[] validateAndRead(MultipartFile file) {
        try {
            return imageFileValidator.validateAndRead(
                    file,
                    MAX_FILE_SIZE,
                    MAX_PIXEL_COUNT
            );
        } catch (ImageValidationException exception) {
            if (exception.getFailure() == ImageValidationFailure.FILE_TOO_LARGE) {
                throw new ApiException(
                        ErrorCode.LICENSE_DOCUMENT_TOO_LARGE,
                        "Business license image must not exceed 5 MB"
                );
            }
            throw new ApiException(
                    ErrorCode.INVALID_LICENSE_DOCUMENT,
                    licenseValidationMessage(exception.getFailure())
            );
        }
    }

    private String licenseValidationMessage(ImageValidationFailure failure) {
        return switch (failure) {
            case FILE_REQUIRED -> "Business license image is required";
            case UNSUPPORTED_MEDIA_TYPE -> "Business license must be a JPEG or PNG image";
            case UNREADABLE_FILE -> "Business license image could not be read";
            case INVALID_DIMENSIONS -> "Business license image dimensions are invalid or too large";
            case MEDIA_TYPE_MISMATCH -> "Business license content does not match its media type";
            case INVALID_IMAGE -> "Business license file is not a valid image";
            case FILE_TOO_LARGE -> "Business license image must not exceed 5 MB";
        };
    }

    private void deletePreviousLicenseOrFail(
            String subject,
            MerchantApplicationTransactionService.UploadAttempt attempt
    ) {
        if (attempt.previousStorageKey() == null
                || attempt.previousStorageKey().isBlank()) {
            return;
        }

        try {
            privateImageStorage.deletePrivate(attempt.previousStorageKey());
        } catch (ImageStorageException exception) {
            markUploadFailedWithoutMaskingOriginalError(subject, attempt.submissionId());
            throw new ApiException(
                    ErrorCode.LICENSE_UPLOAD_FAILED,
                    "Previous business license could not be replaced at this time"
            );
        }
    }

    private void deleteWithoutMaskingOriginalError(String storageKey) {
        try {
            privateImageStorage.deletePrivate(storageKey);
        } catch (RuntimeException cleanupException) {
            log.error("Could not clean up unreferenced private merchant license", cleanupException);
        }
    }

    private void markUploadFailedWithoutMaskingOriginalError(
            String subject,
            java.util.UUID submissionId
    ) {
        try {
            transactionService.markUploadFailed(subject, submissionId);
        } catch (RuntimeException exception) {
            log.error("Could not record failed merchant application upload", exception);
        }
    }
}
