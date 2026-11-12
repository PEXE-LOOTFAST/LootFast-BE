package com.lootfast.backend.auth.dto;

import com.lootfast.backend.auth.social.SocialProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SocialLoginRequest(
        @NotNull(message = "Social provider is required")
        SocialProvider provider,

        @NotBlank(message = "Identity token is required")
        @Size(max = 8192, message = "Identity token is too large")
        String idToken,

        @NotBlank(message = "Nonce is required")
        @Size(max = 200, message = "Nonce is too large")
        String nonce,

        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,

        Boolean termsAccepted,

        @Size(max = 50, message = "Terms version must not exceed 50 characters")
        String termsVersion,

        Boolean privacyPolicyAccepted,

        @Size(max = 50, message = "Privacy policy version must not exceed 50 characters")
        String privacyPolicyVersion
) {
    @Override
    public String toString() {
        return "SocialLoginRequest[provider=" + provider + ", credentials=REDACTED]";
    }
}
