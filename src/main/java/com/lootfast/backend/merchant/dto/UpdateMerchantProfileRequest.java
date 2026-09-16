package com.lootfast.backend.merchant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMerchantProfileRequest(
        @NotBlank(message = "Store name is required")
        @Size(max = 120, message = "Store name must not exceed 120 characters")
        String storeName,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        @NotBlank(message = "Business category is required")
        @Size(max = 100, message = "Business category must not exceed 100 characters")
        String businessCategory,

        @NotBlank(message = "Contact email is required")
        @Email(message = "Contact email must be valid")
        @Size(max = 254, message = "Contact email must not exceed 254 characters")
        String contactEmail,

        @NotBlank(message = "Contact phone is required")
        @Pattern(
                regexp = "^\\+?[0-9]{8,15}$",
                message = "Contact phone must contain 8 to 15 digits with an optional leading +"
        )
        String contactPhone
) {
}
