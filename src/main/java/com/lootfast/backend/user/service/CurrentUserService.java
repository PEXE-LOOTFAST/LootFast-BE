package com.lootfast.backend.user.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.user.dto.CurrentUserResponse;
import com.lootfast.backend.user.dto.DiscoveryPreferencesResponse;
import com.lootfast.backend.user.dto.UpdateDiscoveryPreferencesRequest;
import com.lootfast.backend.user.dto.UpdateProfileRequest;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserProfile;
import com.lootfast.backend.user.repository.UserProfileRepository;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final com.lootfast.backend.content.service.CategoryService categories;

    public CurrentUserService(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            com.lootfast.backend.content.service.CategoryService categories
    ) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(String subject) {
        Long userId = parseUserId(subject);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND,
                        "Authenticated user is no longer available"));
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);

        return buildResponse(user, profile);
    }

    @Transactional(timeout = 5)
    public CurrentUserResponse updateProfile(String subject, UpdateProfileRequest request) {
        Long userId = parseUserId(subject);
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND,
                        "Authenticated user is no longer available"));

        String firstName = trimToNull(request.firstName());
        String lastName = trimToNull(request.lastName());
        String phoneNumber = trimToNull(request.phoneNumber());
        String displayName = trimToNull(request.displayName());

        // Chỉ kiểm tra trùng khi số điện thoại thực sự đổi (tránh báo trùng chính nó).
        if (phoneNumber != null
                && !phoneNumber.equals(user.getPhoneNumber())
                && userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new ApiException(ErrorCode.DUPLICATE_PHONE,
                    "Phone number is already registered");
        }

        // Partial update: field null giữ nguyên giá trị cũ.
        user.updateBasicInfo(
                firstName != null ? firstName : user.getFirstName(),
                lastName != null ? lastName : user.getLastName(),
                phoneNumber != null ? phoneNumber : user.getPhoneNumber()
        );

        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> new UserProfile(user, defaultDisplayName(user)));
        if (displayName != null) {
            profile.rename(displayName);
        }
        userProfileRepository.save(profile);

        return buildResponse(user, profile);
    }

    private CurrentUserResponse buildResponse(User user, UserProfile profile) {
        return new CurrentUserResponse(
                user.getPublicId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getRoles(),
                profile == null ? defaultDisplayName(user) : profile.getDisplayName(),
                profile == null ? null : profile.getAvatarUrl(),
                profile == null ? null : profile.getPickupArea(),
                profile == null
                        ? List.of()
                        : profile.getFavoriteCategories(),
                user.getCreatedAt()
        );
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Transactional(timeout = 5)
    public DiscoveryPreferencesResponse updateDiscoveryPreferences(
            String subject,
            UpdateDiscoveryPreferencesRequest request
    ) {
        Long userId = parseUserId(subject);
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND,
                        "Authenticated user is no longer available"));
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> new UserProfile(user, defaultDisplayName(user)));
        request.favoriteCategories().stream().map(c -> c.trim().toUpperCase(java.util.Locale.ROOT))
                .distinct().sorted().forEach(categories::requireEnabled);
        profile.updateDiscoveryPreferences(request.pickupArea(), request.favoriteCategories());
        return DiscoveryPreferencesResponse.from(userProfileRepository.save(profile));
    }

    private String defaultDisplayName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }

    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException | NullPointerException e) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND,
                    "Authenticated user is no longer available");
        }
    }
}
