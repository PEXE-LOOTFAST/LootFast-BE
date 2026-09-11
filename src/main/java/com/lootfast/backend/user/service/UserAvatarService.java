package com.lootfast.backend.user.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.storage.AvatarStorage;
import com.lootfast.backend.common.storage.AvatarStorageException;
import com.lootfast.backend.common.storage.StoredAvatar;
import com.lootfast.backend.user.dto.AvatarResponse;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserProfile;
import com.lootfast.backend.user.repository.UserProfileRepository;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

@Service
public class UserAvatarService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_PIXEL_COUNT = 25_000_000L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png"
    );

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final AvatarStorage avatarStorage;

    public UserAvatarService(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            AvatarStorage avatarStorage
    ) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.avatarStorage = avatarStorage;
    }

    @Transactional
    public AvatarResponse uploadAvatar(String subject, MultipartFile file) {
        byte[] content = validateAndRead(file);
        Long userId = parseUserId(subject);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND,
                        "Authenticated user is no longer available"));
        UserProfile profile = userProfileRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "User profile is missing for authenticated user"
                ));

        StoredAvatar storedAvatar;
        try {
            storedAvatar = avatarStorage.upload(user.getPublicId(), content);
        } catch (AvatarStorageException exception) {
            throw new ApiException(
                    ErrorCode.AVATAR_UPLOAD_FAILED,
                    "Avatar could not be uploaded at this time"
            );
        }

        profile.replaceAvatar(storedAvatar.storageKey(), storedAvatar.url());
        return new AvatarResponse(storedAvatar.url());
    }

    private byte[] validateAndRead(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidAvatar("Avatar file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(
                    ErrorCode.AVATAR_TOO_LARGE,
                    "Avatar file must not exceed 5 MB"
            );
        }

        String contentType = file.getContentType();
        if (contentType == null
                || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw invalidAvatar("Avatar must be a JPEG or PNG image");
        }

        try {
            byte[] content = file.getBytes();
            validateImageContent(content, contentType);
            return content;
        } catch (IOException exception) {
            throw invalidAvatar("Avatar file could not be read");
        }
    }

    private void validateImageContent(byte[] content, String contentType) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(content)
        )) {
            if (input == null) {
                throw invalidAvatar("Avatar file is not a valid image");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidAvatar("Avatar file is not a valid image");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long pixelCount = (long) width * height;

                if (width <= 0 || height <= 0 || pixelCount > MAX_PIXEL_COUNT) {
                    throw invalidAvatar("Avatar dimensions are invalid or too large");
                }

                String actualFormat = reader.getFormatName().toLowerCase(Locale.ROOT);
                boolean formatMatches = (contentType.equalsIgnoreCase("image/png")
                        && actualFormat.equals("png"))
                        || (contentType.equalsIgnoreCase("image/jpeg")
                        && (actualFormat.equals("jpeg") || actualFormat.equals("jpg")));
                if (!formatMatches) {
                    throw invalidAvatar("Avatar content does not match its media type");
                }
            } finally {
                reader.dispose();
            }
        }
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
