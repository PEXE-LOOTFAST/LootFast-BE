package com.lootfast.backend.merchant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateOpeningHoursRequest(
        @NotNull(message = "Opening hour slots are required")
        @Size(max = 35, message = "A store can have at most 35 opening hour slots")
        List<@Valid OpeningHourSlotRequest> slots
) {
}
