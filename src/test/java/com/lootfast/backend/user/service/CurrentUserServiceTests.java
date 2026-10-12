package com.lootfast.backend.user.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.user.dto.CurrentUserResponse;
import com.lootfast.backend.user.dto.UpdateProfileRequest;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserProfile;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserProfileRepository;
import com.lootfast.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock private com.lootfast.backend.content.service.CategoryService categories;
    private CurrentUserService currentUserService;

    @BeforeEach
    void setUp() {
        currentUserService = new CurrentUserService(
                userRepository,
                userProfileRepository,
                categories
        );
    }

    @Test
    void getCurrentUserReturnsLatestProfileFromDatabase() {
        User user = mock(User.class);
        UserProfile profile = mock(UserProfile.class);
        UUID publicId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        Instant createdAt = Instant.parse("2026-09-08T08:00:00Z");

        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(user.getPublicId()).thenReturn(publicId);
        when(user.getFirstName()).thenReturn("Kien");
        when(user.getLastName()).thenReturn("Nguyen Van");
        when(user.getEmail()).thenReturn("kien@gmail.com");
        when(user.getPhoneNumber()).thenReturn("912345678");
        when(user.getRole()).thenReturn(UserRole.BUYER);
        when(user.getRoles()).thenReturn(Set.of(UserRole.BUYER));
        when(user.getCreatedAt()).thenReturn(createdAt);
        when(userProfileRepository.findByUserId(42L)).thenReturn(Optional.of(profile));
        when(profile.getDisplayName()).thenReturn("Kien foodie");
        when(profile.getAvatarUrl()).thenReturn("https://cdn.lootfast.test/avatar.png");
        when(profile.getPickupArea()).thenReturn("District 9");
        when(profile.getFavoriteCategories()).thenReturn(List.of("BAKERY", "VEGETARIAN"));

        CurrentUserResponse response = currentUserService.getCurrentUser("42");

        assertEquals(publicId, response.publicId());
        assertEquals("Kien", response.firstName());
        assertEquals("Nguyen Van", response.lastName());
        assertEquals("kien@gmail.com", response.email());
        assertEquals("912345678", response.phoneNumber());
        assertEquals(UserRole.BUYER, response.role());
        assertEquals("Kien foodie", response.displayName());
        assertEquals("https://cdn.lootfast.test/avatar.png", response.avatarUrl());
        assertEquals("District 9", response.pickupArea());
        assertEquals(Set.of("BAKERY", "VEGETARIAN"), Set.copyOf(response.favoriteCategories()));
        assertEquals(createdAt, response.createdAt());
    }

    @Test
    void getCurrentUserFallsBackToAccountNameWhenLegacyProfileIsMissing() {
        User user = mock(User.class);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(user.getFirstName()).thenReturn("Kien");
        when(user.getLastName()).thenReturn("Nguyen Van");
        when(userProfileRepository.findByUserId(42L)).thenReturn(Optional.empty());

        CurrentUserResponse response = currentUserService.getCurrentUser("42");

        assertEquals("Kien Nguyen Van", response.displayName());
        assertEquals(null, response.avatarUrl());
        assertEquals(null, response.pickupArea());
        assertEquals(0, response.favoriteCategories().size());
    }

    @Test
    void getCurrentUserRejectsInvalidTokenSubject() {
        ApiException ex = assertThrows(ApiException.class,
                () -> currentUserService.getCurrentUser("not-a-number"));

        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
        verifyNoInteractions(userRepository, userProfileRepository);
    }

    @Test
    void getCurrentUserRejectsDeletedUser() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> currentUserService.getCurrentUser("42"));

        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
        assertEquals("Authenticated user is no longer available", ex.getMessage());
    }

    @Test
    void updateProfileChangesOnlyProvidedFields() {
        User user = newUser();
        UserProfile profile = new UserProfile(user, "Kien foodie");
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(42L)).thenReturn(Optional.of(profile));

        CurrentUserResponse response = currentUserService.updateProfile(
                "42",
                new UpdateProfileRequest(null, null, null, "New display name"));

        assertEquals("Kien", response.firstName());
        assertEquals("Nguyen Van", response.lastName());
        assertEquals("912345678", response.phoneNumber());
        assertEquals("New display name", response.displayName());
        // Không đổi số điện thoại thì không kiểm tra trùng.
        verify(userRepository, never()).existsByPhoneNumber(any());
    }

    @Test
    void updateProfileUpdatesNameAndPhoneWhenProvided() {
        User user = newUser();
        UserProfile profile = new UserProfile(user, "Kien foodie");
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(42L)).thenReturn(Optional.of(profile));
        when(userRepository.existsByPhoneNumber("900000000")).thenReturn(false);

        CurrentUserResponse response = currentUserService.updateProfile(
                "42",
                new UpdateProfileRequest("Khanh", null, "900000000", null));

        assertEquals("Khanh", response.firstName());
        assertEquals("Nguyen Van", response.lastName());
        assertEquals("900000000", response.phoneNumber());
        assertEquals("Kien foodie", response.displayName());
    }

    @Test
    void updateProfileRejectsDuplicatePhone() {
        User user = newUser();
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
        when(userRepository.existsByPhoneNumber("900000000")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> currentUserService.updateProfile(
                        "42",
                        new UpdateProfileRequest(null, null, "900000000", null)));

        assertEquals(ErrorCode.DUPLICATE_PHONE, ex.getErrorCode());
    }

    @Test
    void updateProfileSkipsDuplicateCheckWhenPhoneUnchanged() {
        User user = newUser();
        UserProfile profile = new UserProfile(user, "Kien foodie");
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(42L)).thenReturn(Optional.of(profile));

        currentUserService.updateProfile(
                "42",
                new UpdateProfileRequest("Khanh", null, "912345678", null));

        verify(userRepository, never()).existsByPhoneNumber(any());
    }

    @Test
    void updateProfileRejectsDeletedUser() {
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> currentUserService.updateProfile(
                        "42",
                        new UpdateProfileRequest("Khanh", null, null, null)));

        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    private static User newUser() {
        return new User(
                "Kien",
                "Nguyen Van",
                "kien@gmail.com",
                "912345678",
                "hash",
                UserRole.BUYER,
                UserStatus.UNVERIFIED);
    }
}
