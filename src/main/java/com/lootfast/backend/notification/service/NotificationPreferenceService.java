package com.lootfast.backend.notification.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.notification.dto.NotificationPreferenceResponse;
import com.lootfast.backend.notification.dto.UpdateNotificationPreferenceRequest;
import com.lootfast.backend.notification.entity.NotificationPreference;
import com.lootfast.backend.notification.repository.NotificationPreferenceRepository;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceService {

    private final UserRepository userRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationPreferenceService(
            UserRepository userRepository,
            NotificationPreferenceRepository preferenceRepository
    ) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceResponse get(String subject) {
        Long userId = requireUserId(subject);
        if (!userRepository.existsById(userId)) {
            throw userNotFound();
        }

        return preferenceRepository.findByUserId(userId)
                .map(NotificationPreferenceResponse::from)
                .orElseGet(NotificationPreferenceResponse::defaults);
    }

    @Transactional(timeout = 5)
    public NotificationPreferenceResponse update(
            String subject,
            UpdateNotificationPreferenceRequest request
    ) {
        Long userId = requireUserId(subject);
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(this::userNotFound);
        NotificationPreference preference = preferenceRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> new NotificationPreference(user));

        preference.update(
                request.matchingDeals(),
                request.buyerOrderUpdates(),
                request.merchantOrderUpdates(),
                request.pickupReminders(),
                request.emailEnabled()
        );

        return NotificationPreferenceResponse.from(preferenceRepository.save(preference));
    }

    private Long requireUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException | NullPointerException exception) {
            throw userNotFound();
        }
    }

    private ApiException userNotFound() {
        return new ApiException(
                ErrorCode.USER_NOT_FOUND,
                "Authenticated user is no longer available"
        );
    }
}
