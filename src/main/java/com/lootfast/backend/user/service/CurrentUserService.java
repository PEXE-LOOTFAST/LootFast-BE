package com.lootfast.backend.user.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.user.dto.CurrentUserResponse;
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

    public CurrentUserService(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository
    ) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(String subject) {
        Long userId = parseUserId(subject);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND,
                        "Authenticated user is no longer available"));
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);

        return new CurrentUserResponse(
                user.getPublicId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole(),
                profile == null ? defaultDisplayName(user) : profile.getDisplayName(),
                profile == null ? null : profile.getAvatarUrl(),
                profile == null ? null : profile.getPickupArea(),
                profile == null
                        ? List.of()
                        : profile.getFavoriteCategories(),
                user.getCreatedAt()
        );
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
