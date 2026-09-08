package com.lootfast.backend.auth.service;

import com.lootfast.backend.auth.dto.SigninRequest;
import com.lootfast.backend.auth.dto.SigninResponse;
import com.lootfast.backend.auth.exception.InvalidCredentialsException;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class SigninService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public SigninService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public SigninResponse signin(SigninRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(this::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        JwtService.IssuedToken accessToken = jwtService.issueAccessToken(user);

        return new SigninResponse(
                accessToken.value(),
                "Bearer",
                accessToken.expiresInSeconds(),
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole()
        );
    }

    private InvalidCredentialsException invalidCredentials() {
        return new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
    }
}
