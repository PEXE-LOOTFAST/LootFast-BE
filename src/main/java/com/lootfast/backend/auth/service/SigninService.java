package com.lootfast.backend.auth.service;

import com.lootfast.backend.auth.dto.SigninRequest;
import com.lootfast.backend.auth.dto.SigninResponse;
import com.lootfast.backend.auth.session.AuthSessionService;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class SigninService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthSessionService authSessionService;

    public SigninService(UserRepository userRepository,
                         PasswordEncoder passwordEncoder,
                         JwtService jwtService,
                         AuthSessionService authSessionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authSessionService = authSessionService;
    }

    @Transactional
    public SigninResponse signin(SigninRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS,
                        "Invalid email or password"));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }

        // Check sau password để không lộ thông tin: email tồn tại mà chưa verify
        if (user.getStatus() == com.lootfast.backend.user.entity.UserStatus.UNVERIFIED) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_VERIFIED,
                    "Please verify your email before signing in");
        }

        JwtService.IssuedToken token = jwtService.issueAccessToken(user);
        AuthSessionService.IssuedSession session = authSessionService.createSession(user);

        return new SigninResponse(
                token.value(),
                session.refreshToken(),
                "Bearer",
                token.expiresInSeconds(),
                session.expiresInSeconds(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                user.getRoles()
        );
    }
}
