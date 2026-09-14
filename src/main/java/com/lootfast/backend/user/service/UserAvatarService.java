package com.lootfast.backend.user.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.storage.ImageFileValidator;
import com.lootfast.backend.common.storage.ImageStorage;
import com.lootfast.backend.common.storage.ImageStorageException;
import com.lootfast.backend.common.storage.ImageUploadPreset;
import com.lootfast.backend.common.storage.ImageValidationException;
import com.lootfast.backend.common.storage.ImageValidationFailure;
import com.lootfast.backend.common.storage.StoredImage;
import com.lootfast.backend.user.dto.AvatarResponse;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.repository.UserProfileRepository;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

@Service
public class UserAvatarService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_PIXEL_COUNT = 25_000_000L;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final ImageStorage imageStorage;
    private final ImageFileValidator imageFileValidator;

    public UserAvatarService(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            ImageStorage imageStorage,
            ImageFileValidator imageFileValidator
    ) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.imageStorage = imageStorage;
        this.imageFileValidator = imageFileValidator;
    }

    public AvatarResponse uploadAvatar(String subject, MultipartFile file) {
        byte[] content = validateAndRead(file);
        Long userId = parseUserId(subject);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND,
                        "Authenticated user is no longer available"));
        if (!userProfileRepository.existsByUserId(userId)) {
            throw new IllegalStateException("User profile is missing for authenticated user");
        }

        String storageKey = "lootfast/user-avatars/" + user.getPublicId();
        StoredImage storedImage;
        try {
            storedImage = imageStorage.upload(
                    storageKey,
                    content,
                    ImageUploadPreset.USER_AVATAR
            );
        } catch (ImageStorageException exception) {
            throw new ApiException(
                    ErrorCode.AVATAR_UPLOAD_FAILED,
                    "Avatar could not be uploaded at this time"
            );
        }

        int updated = userProfileRepository.updateAvatarByUserId(
                userId,
                storedImage.storageKey(),
                storedImage.url(),
                Instant.now()
        );
        if (updated == 0) {
            throw new IllegalStateException("User profile disappeared during avatar upload");
        }
        return new AvatarResponse(storedImage.url());
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
                        ErrorCode.AVATAR_TOO_LARGE,
                        "Avatar file must not exceed 5 MB"
                );
            }
            throw invalidAvatar(validationMessage(exception.getFailure()));
        }
    }

    private String validationMessage(ImageValidationFailure failure) {
        return switch (failure) {
            case FILE_REQUIRED -> "Avatar file is required";
            case UNSUPPORTED_MEDIA_TYPE -> "Avatar must be a JPEG or PNG image";
            case UNREADABLE_FILE -> "Avatar file could not be read";
            case INVALID_DIMENSIONS -> "Avatar dimensions are invalid or too large";
            case MEDIA_TYPE_MISMATCH -> "Avatar content does not match its media type";
            case INVALID_IMAGE -> "Avatar file is not a valid image";
            case FILE_TOO_LARGE -> "Avatar file must not exceed 5 MB";
        };
    }

    private ApiException invalidAvatar(String message) {
        return new ApiException(ErrorCode.INVALID_AVATAR, message);
    }

    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException | NullPointerException exception) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND,
                    "Authenticated user is no longer available");
        }
    }
}
