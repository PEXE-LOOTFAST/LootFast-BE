package com.lootfast.backend.merchant.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.storage.ImageFileValidator;
import com.lootfast.backend.common.storage.ImageStorage;
import com.lootfast.backend.common.storage.ImageStorageException;
import com.lootfast.backend.common.storage.ImageValidationException;
import com.lootfast.backend.common.storage.ImageValidationFailure;
import com.lootfast.backend.common.storage.StoredImage;
import com.lootfast.backend.merchant.dto.MerchantImageResponse;
import com.lootfast.backend.merchant.entity.MerchantImageType;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

@Service
public class MerchantImageService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_PIXEL_COUNT = 25_000_000L;

    private final UserRepository userRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final ImageStorage imageStorage;
    private final ImageFileValidator imageFileValidator;
    private final MerchantImageUploadRateLimiter uploadRateLimiter;

    public MerchantImageService(
            UserRepository userRepository,
            MerchantProfileRepository merchantProfileRepository,
            ImageStorage imageStorage,
            ImageFileValidator imageFileValidator,
            MerchantImageUploadRateLimiter uploadRateLimiter
    ) {
        this.userRepository = userRepository;
        this.merchantProfileRepository = merchantProfileRepository;
        this.imageStorage = imageStorage;
        this.imageFileValidator = imageFileValidator;
        this.uploadRateLimiter = uploadRateLimiter;
    }

    public MerchantImageResponse uploadLogo(String subject, MultipartFile file) {
        return upload(subject, file, MerchantImageType.LOGO);
    }

    public MerchantImageResponse uploadCover(String subject, MultipartFile file) {
        return upload(subject, file, MerchantImageType.COVER);
    }

    private MerchantImageResponse upload(
            String subject,
            MultipartFile file,
            MerchantImageType imageType
    ) {
        Long userId = parseUserId(subject);
        uploadRateLimiter.requireAllowed(userId);
        byte[] content = validateAndRead(file, imageType);
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.USER_NOT_FOUND,
                        "Authenticated user is no longer available"
                ));

        if (!user.hasRole(UserRole.MERCHANT)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Access is denied");
        }
        if (!merchantProfileRepository.existsByUserId(userId)) {
            throw new ApiException(
                    ErrorCode.MERCHANT_PROFILE_NOT_FOUND,
                    "Merchant profile has not been created"
            );
        }

        String storageKey = "lootfast/merchant-profiles/"
                + user.getPublicId()
                + "/"
                + imageType.storageName();

        StoredImage storedImage;
        try {
            storedImage = imageStorage.upload(
                    storageKey,
                    content,
                    imageType.uploadPreset()
            );
        } catch (ImageStorageException exception) {
            throw new ApiException(
                    ErrorCode.MERCHANT_IMAGE_UPLOAD_FAILED,
                    "Merchant image could not be uploaded at this time"
            );
        }

        int updated = updateProfileImage(userId, imageType, storedImage);
        if (updated == 0) {
            throw new ApiException(
                    ErrorCode.MERCHANT_PROFILE_NOT_FOUND,
                    "Merchant profile disappeared during image upload"
            );
        }

        return new MerchantImageResponse(imageType, storedImage.url());
    }

    private int updateProfileImage(
            Long userId,
            MerchantImageType imageType,
            StoredImage storedImage
    ) {
        Instant updatedAt = Instant.now();
        return switch (imageType) {
            case LOGO -> merchantProfileRepository.updateLogoByUserId(
                    userId,
                    storedImage.storageKey(),
                    storedImage.url(),
                    updatedAt
            );
            case COVER -> merchantProfileRepository.updateCoverByUserId(
                    userId,
                    storedImage.storageKey(),
                    storedImage.url(),
                    updatedAt
            );
        };
    }

    private byte[] validateAndRead(
            MultipartFile file,
            MerchantImageType imageType
    ) {
        try {
            return imageFileValidator.validateAndRead(
                    file,
                    MAX_FILE_SIZE,
                    MAX_PIXEL_COUNT
            );
        } catch (ImageValidationException exception) {
            if (exception.getFailure() == ImageValidationFailure.FILE_TOO_LARGE) {
                throw new ApiException(
                        ErrorCode.MERCHANT_IMAGE_TOO_LARGE,
                        "Merchant image file must not exceed 5 MB"
                );
            }
            throw new ApiException(
                    ErrorCode.INVALID_MERCHANT_IMAGE,
                    validationMessage(imageType, exception.getFailure())
            );
        }
    }

    private String validationMessage(
            MerchantImageType imageType,
            ImageValidationFailure failure
    ) {
        String label = imageType == MerchantImageType.LOGO
                ? "Merchant logo"
                : "Merchant cover";
        return switch (failure) {
            case FILE_REQUIRED -> label + " file is required";
            case UNSUPPORTED_MEDIA_TYPE -> label + " must be a JPEG or PNG image";
            case UNREADABLE_FILE -> label + " file could not be read";
            case INVALID_DIMENSIONS -> label + " dimensions are invalid or too large";
            case MEDIA_TYPE_MISMATCH -> label + " content does not match its media type";
            case INVALID_IMAGE -> label + " file is not a valid image";
            case FILE_TOO_LARGE -> label + " file must not exceed 5 MB";
        };
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
}
