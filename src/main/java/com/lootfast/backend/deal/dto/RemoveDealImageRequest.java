package com.lootfast.backend.deal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RemoveDealImageRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
