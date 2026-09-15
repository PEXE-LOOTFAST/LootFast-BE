package com.lootfast.backend.auth.dto;

import com.lootfast.backend.user.entity.AccountType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        @Pattern(regexp = "(?i)^[A-Z0-9._%+-]+@gmail\\.com$", message = "Email must be a Gmail address")
        String email,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\d{9}$", message = "Phone number must contain exactly 9 digits")
        String phoneNumber,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password,

        @NotBlank(message = "Password confirmation is required")
        @Size(min = 8, max = 72, message = "Password confirmation must be between 8 and 72 characters")
        String confirmPassword,

        @NotNull(message = "Account type is required")
        AccountType accountType,

        @AssertTrue(message = "Terms of use must be accepted")
        boolean termsAccepted,

        @NotBlank(message = "Terms version is required")
        @Size(max = 50, message = "Terms version must not exceed 50 characters")
        String termsVersion,

        @AssertTrue(message = "Privacy policy must be accepted")
        boolean privacyPolicyAccepted,

        @NotBlank(message = "Privacy policy version is required")
        @Size(max = 50, message = "Privacy policy version must not exceed 50 characters")
        String privacyPolicyVersion
) {
}
