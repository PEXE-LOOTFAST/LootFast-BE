package com.lootfast.backend.auth.service;

import com.lootfast.backend.auth.dto.SignupRequest;
import com.lootfast.backend.auth.dto.SignupResponse;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class SignupService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;

    public SignupService(UserRepository userRepository,
                         PasswordEncoder passwordEncoder,
                         OtpService otpService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new ApiException(ErrorCode.PASSWORD_MISMATCH,
                    "Password confirmation does not match");
        }

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String normalizedPhone = request.phoneNumber().trim();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL, "Email is already registered");
        }

        if (userRepository.existsByPhoneNumber(normalizedPhone)) {
            throw new ApiException(ErrorCode.DUPLICATE_PHONE,
                    "Phone number is already registered");
        }

        User user = new User(
                request.firstName().trim(),
                request.lastName().trim(),
                normalizedEmail,
                normalizedPhone,
                passwordEncoder.encode(request.password()),
                UserRole.BUYER,
                UserStatus.UNVERIFIED
        );

        userRepository.save(user);

        // OtpService tự gửi email @Async — không block transaction này
        otpService.sendOtp(normalizedEmail);

        return new SignupResponse(
                "Verification code sent to " + normalizedEmail,
                normalizedEmail
        );
    }
}
