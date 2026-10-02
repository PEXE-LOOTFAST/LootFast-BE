package com.lootfast.backend.notification.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.notification.dto.NotificationResponse;
import com.lootfast.backend.notification.dto.ReadAllNotificationsResponse;
import com.lootfast.backend.notification.dto.UnreadNotificationCountResponse;
import com.lootfast.backend.notification.repository.UserNotificationRepository;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class UserNotificationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserNotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public UserNotificationService(
            UserNotificationRepository notificationRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> list(String subject, int page, int size) {
        Long userId = requireExistingUser(subject);
        validatePage(page, size);
        var result = notificationRepository.findByRecipientId(
                userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new PagedResponse<>(
                result.getContent().stream().map(NotificationResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse unreadCount(String subject) {
        Long userId = requireExistingUser(subject);
        return new UnreadNotificationCountResponse(
                notificationRepository.countByRecipientIdAndReadAtIsNull(userId)
        );
    }

    @Transactional(timeout = 5)
    public NotificationResponse markRead(String subject, UUID notificationId) {
        Long userId = requireExistingUser(subject);
        var notification = notificationRepository.findOwnedForUpdate(notificationId, userId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOTIFICATION_NOT_FOUND,
                        "Notification was not found"
                ));
        notification.markRead(clock.instant());
        return NotificationResponse.from(notification);
    }

    @Transactional(timeout = 5)
    public ReadAllNotificationsResponse markAllRead(String subject) {
        Long userId = requireExistingUser(subject);
        return new ReadAllNotificationsResponse(
                notificationRepository.markAllRead(userId, clock.instant())
        );
    }

    private Long requireExistingUser(String subject) {
        Long userId;
        try {
            userId = Long.valueOf(subject);
        } catch (NumberFormatException | NullPointerException exception) {
            throw userNotFound();
        }
        if (!userRepository.existsById(userId)) {
            throw userNotFound();
        }
        return userId;
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "page must be at least 0 and size must be between 1 and 100"
            );
        }
    }

    private ApiException userNotFound() {
        return new ApiException(
                ErrorCode.USER_NOT_FOUND,
                "Authenticated user is no longer available"
        );
    }
}
