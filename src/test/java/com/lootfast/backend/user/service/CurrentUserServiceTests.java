package com.lootfast.backend.user.service;

import com.lootfast.backend.user.dto.CurrentUserResponse;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.exception.AuthenticatedUserNotFoundException;
import com.lootfast.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTests {

    @Mock
    private UserRepository userRepository;

    private CurrentUserService currentUserService;

    @BeforeEach
    void setUp() {
        currentUserService = new CurrentUserService(userRepository);
    }

    @Test
    void getCurrentUserReturnsLatestProfileFromDatabase() {
        User user = mock(User.class);
        Instant createdAt = Instant.parse("2026-09-08T08:00:00Z");
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(42L);
        when(user.getFirstName()).thenReturn("Kien");
        when(user.getLastName()).thenReturn("Nguyen Van");
        when(user.getEmail()).thenReturn("kien@gmail.com");
        when(user.getPhoneNumber()).thenReturn("912345678");
        when(user.getRole()).thenReturn(UserRole.BUYER);
        when(user.getCreatedAt()).thenReturn(createdAt);

        CurrentUserResponse response = currentUserService.getCurrentUser("42");

        assertEquals(42L, response.id());
        assertEquals("Kien", response.firstName());
        assertEquals("Nguyen Van", response.lastName());
        assertEquals("kien@gmail.com", response.email());
        assertEquals("912345678", response.phoneNumber());
        assertEquals(UserRole.BUYER, response.role());
        assertEquals(createdAt, response.createdAt());
    }

    @Test
    void getCurrentUserRejectsInvalidTokenSubject() {
        assertThrows(
                AuthenticatedUserNotFoundException.class,
                () -> currentUserService.getCurrentUser("not-a-user-id")
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void getCurrentUserRejectsDeletedUser() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        AuthenticatedUserNotFoundException exception = assertThrows(
                AuthenticatedUserNotFoundException.class,
                () -> currentUserService.getCurrentUser("42")
        );

        assertEquals("Authenticated user is no longer available", exception.getMessage());
    }
}
