package com.lootfast.backend.auth.service;

import com.lootfast.backend.auth.dto.SignupRequest;
import com.lootfast.backend.auth.dto.SignupResponse;
import com.lootfast.backend.auth.exception.DuplicateUserException;
import com.lootfast.backend.auth.exception.PasswordMismatchException;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignupServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private SignupService signupService;

    @BeforeEach
    void setUp() {
        signupService = new SignupService(userRepository, passwordEncoder);
    }

    @Test
    void signupCreatesBuyerWithNormalizedEmailAndEncodedPassword() {
        SignupRequest request = request("New.User@Gmail.com", "912345678", "password123");
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SignupResponse response = signupService.signup(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertEquals("new.user@gmail.com", savedUser.getEmail());
        assertEquals("encoded-password", savedUser.getPasswordHash());
        assertEquals(UserRole.BUYER, savedUser.getRole());
        assertEquals("new.user@gmail.com", response.email());
        assertEquals(UserRole.BUYER, response.role());
    }

    @Test
    void signupRejectsMismatchedPasswordConfirmation() {
        SignupRequest request = new SignupRequest(
                "An", "Nguyen", "an@gmail.com", "912345678", "password123", "different123"
        );

        assertThrows(PasswordMismatchException.class, () -> signupService.signup(request));

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void signupRejectsExistingEmail() {
        SignupRequest request = request("existing@gmail.com", "912345678", "password123");
        when(userRepository.existsByEmailIgnoreCase("existing@gmail.com")).thenReturn(true);

        assertThrows(DuplicateUserException.class, () -> signupService.signup(request));

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void signupRejectsExistingPhoneNumber() {
        SignupRequest request = request("new@gmail.com", "912345678", "password123");
        when(userRepository.existsByPhoneNumber("912345678")).thenReturn(true);

        DuplicateUserException exception = assertThrows(
                DuplicateUserException.class,
                () -> signupService.signup(request)
        );

        assertTrue(exception.getMessage().contains("Phone number"));
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }

    private SignupRequest request(String email, String phoneNumber, String password) {
        return new SignupRequest("An", "Nguyen Van", email, phoneNumber, password, password);
    }
}
