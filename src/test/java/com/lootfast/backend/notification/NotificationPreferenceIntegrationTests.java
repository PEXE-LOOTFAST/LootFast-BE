package com.lootfast.backend.notification;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.notification.dto.NotificationPreferenceResponse;
import com.lootfast.backend.notification.dto.UpdateNotificationPreferenceRequest;
import com.lootfast.backend.notification.repository.NotificationPreferenceRepository;
import com.lootfast.backend.notification.service.NotificationPreferenceService;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class NotificationPreferenceIntegrationTests {

    @Autowired
    private NotificationPreferenceService preferenceService;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void returnsDefaultsWithoutCreatingARecord() {
        User user = saveUser("buyer-defaults@example.com", "111111111");

        NotificationPreferenceResponse response = preferenceService.get(user.getId().toString());

        assertTrue(response.matchingDeals());
        assertTrue(response.buyerOrderUpdates());
        assertTrue(response.merchantOrderUpdates());
        assertTrue(response.pickupReminders());
        assertFalse(response.emailEnabled());
        assertEquals(0, preferenceRepository.count());
    }

    @Test
    void updatePersistsEveryNotificationCategory() {
        User user = saveUser("buyer-update@example.com", "222222222");
        UpdateNotificationPreferenceRequest request = new UpdateNotificationPreferenceRequest(
                false,
                true,
                false,
                true,
                true
        );

        NotificationPreferenceResponse updated = preferenceService.update(
                user.getId().toString(),
                request
        );
        NotificationPreferenceResponse reloaded = preferenceService.get(user.getId().toString());

        assertEquals(updated, reloaded);
        assertFalse(reloaded.matchingDeals());
        assertTrue(reloaded.buyerOrderUpdates());
        assertFalse(reloaded.merchantOrderUpdates());
        assertTrue(reloaded.pickupReminders());
        assertTrue(reloaded.emailEnabled());
        assertTrue(preferenceRepository.findByUserId(user.getId()).isPresent());
    }

    @Test
    void preferencesRemainIsolatedBetweenUsers() {
        User first = saveUser("buyer-first@example.com", "333333333");
        User second = saveUser("buyer-second@example.com", "444444444");

        preferenceService.update(
                first.getId().toString(),
                new UpdateNotificationPreferenceRequest(false, false, false, false, true)
        );

        NotificationPreferenceResponse secondResponse = preferenceService.get(
                second.getId().toString()
        );
        assertEquals(NotificationPreferenceResponse.defaults(), secondResponse);
        assertTrue(preferenceRepository.findByUserId(second.getId()).isEmpty());
    }

    @Test
    void rejectsADeletedOrInvalidAuthenticatedUser() {
        ApiException missing = assertThrows(
                ApiException.class,
                () -> preferenceService.get(Long.MAX_VALUE + "")
        );
        ApiException invalidSubject = assertThrows(
                ApiException.class,
                () -> preferenceService.update(
                        "invalid-subject",
                        new UpdateNotificationPreferenceRequest(true, true, true, true, false)
                )
        );

        assertEquals(ErrorCode.USER_NOT_FOUND, missing.getErrorCode());
        assertEquals(ErrorCode.USER_NOT_FOUND, invalidSubject.getErrorCode());
    }

    private User saveUser(String email, String phoneNumber) {
        return userRepository.save(new User(
                "Notification",
                "Tester",
                email,
                phoneNumber,
                "encoded-password",
                UserRole.BUYER,
                UserStatus.VERIFIED
        ));
    }
}
