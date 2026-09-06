package com.lootfast.backend.auth.service;

import com.lootfast.backend.auth.dto.SignupRequest;
import com.lootfast.backend.auth.dto.SignupResponse;
import com.lootfast.backend.auth.exception.DuplicateUserException;
import com.lootfast.backend.auth.exception.PasswordMismatchException;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class SignupService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SignupService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new PasswordMismatchException("Password confirmation does not match");
        }

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String normalizedPhoneNumber = request.phoneNumber().trim();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DuplicateUserException("Email is already registered");
        }

        if (userRepository.existsByPhoneNumber(normalizedPhoneNumber)) {
            throw new DuplicateUserException("Phone number is already registered");
        }

        User user = new User(
                request.firstName().trim(),
                request.lastName().trim(),
                normalizedEmail,
                normalizedPhoneNumber,
                passwordEncoder.encode(request.password()),
                UserRole.BUYER
        );

        User savedUser = userRepository.save(user);

        return new SignupResponse(
                savedUser.getId(),
                savedUser.getFirstName(),
                savedUser.getLastName(),
                savedUser.getEmail(),
                savedUser.getPhoneNumber(),
                savedUser.getRole(),
                savedUser.getCreatedAt()
        );
    }
}
