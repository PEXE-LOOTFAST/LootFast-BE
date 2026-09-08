package com.lootfast.backend.user.service;

import com.lootfast.backend.user.dto.CurrentUserResponse;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.exception.AuthenticatedUserNotFoundException;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {

    private static final String USER_NOT_AVAILABLE_MESSAGE =
            "Authenticated user is no longer available";

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(String subject) {
        Long userId = parseUserId(subject);
        User user = userRepository.findById(userId)
                .orElseThrow(this::userNotAvailable);

        return new CurrentUserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException | NullPointerException exception) {
            throw userNotAvailable();
        }
    }

    private AuthenticatedUserNotFoundException userNotAvailable() {
        return new AuthenticatedUserNotFoundException(USER_NOT_AVAILABLE_MESSAGE);
    }
}
