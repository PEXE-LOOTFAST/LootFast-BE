package com.lootfast.backend.auth.service;

import com.lootfast.backend.auth.dto.SigninRequest;
import com.lootfast.backend.auth.dto.SigninResponse;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SigninServiceTests {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    private SigninService signinService;

    @BeforeEach
    void setUp() {
        signinService = new SigninService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void signinReturnsAccessTokenForValidCredentials() {
        User user = user();
        when(userRepository.findByEmailIgnoreCase("kien@gmail.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(jwtService.issueAccessToken(user))
                .thenReturn(new JwtService.IssuedToken("access-token", 900));

        SigninResponse response = signinService.signin(
                new SigninRequest(" Kien@Gmail.com ", "password123"));

        assertEquals("access-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900, response.expiresIn());
        assertEquals("kien@gmail.com", response.email());
        assertEquals(UserRole.BUYER, response.role());
    }

    @Test
    void signinRejectsUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("missing@gmail.com"))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> signinService.signin(new SigninRequest("missing@gmail.com", "password123")));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
        verifyNoInteractions(passwordEncoder, jwtService);
    }

    @Test
    void signinRejectsWrongPassword() {
        User user = user();
        when(userRepository.findByEmailIgnoreCase("kien@gmail.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class,
                () -> signinService.signin(new SigninRequest("kien@gmail.com", "wrong")));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
        verify(jwtService, never()).issueAccessToken(user);
    }

    private User user() {
        return new User("Kien", "Nguyen Van", "kien@gmail.com",
                "912345678", "encoded-password", UserRole.BUYER,
                com.lootfast.backend.user.entity.UserStatus.VERIFIED);
    }
}
