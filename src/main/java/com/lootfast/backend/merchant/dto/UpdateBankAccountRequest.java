package com.lootfast.backend.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Merchant sets/updates their payout account (FR-ME-03). Validation keeps the
 * data VietQR-ready: the bank BIN and account number must be pure digits.
 */
public record UpdateBankAccountRequest(
        @NotBlank(message = "Bank BIN is required")
        @Pattern(regexp = "\\d{6}", message = "Bank BIN must be 6 digits")
        String bankBin,

        @NotBlank(message = "Bank name is required")
        @Size(max = 100, message = "Bank name must be at most 100 characters")
        String bankName,

        @NotBlank(message = "Account number is required")
        @Pattern(regexp = "\\d{6,19}", message = "Account number must be 6-19 digits")
        String accountNumber,

        @NotBlank(message = "Account holder name is required")
        @Size(max = 120, message = "Account holder name must be at most 120 characters")
        String accountHolderName
) {
}
