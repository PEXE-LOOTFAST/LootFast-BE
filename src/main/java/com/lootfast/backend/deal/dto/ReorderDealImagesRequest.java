package com.lootfast.backend.deal.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ReorderDealImagesRequest(
        @NotEmpty @Size(max = 5) List<@NotNull UUID> imageIds
) {
}
