package com.lootfast.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 10, message = "First name must not exceed 10 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 20, message = "Last name must not exceed 20 characters")
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
        String confirmPassword
) {
}
