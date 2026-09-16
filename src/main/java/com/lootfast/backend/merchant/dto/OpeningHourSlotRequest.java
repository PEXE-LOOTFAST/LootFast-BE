package com.lootfast.backend.merchant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record OpeningHourSlotRequest(
        @NotNull(message = "Day of week is required")
        DayOfWeek dayOfWeek,

        @NotNull(message = "Opening time is required")
        @JsonFormat(pattern = "HH:mm")
        LocalTime opensAt,

        @NotNull(message = "Closing time is required")
        @JsonFormat(pattern = "HH:mm")
        LocalTime closesAt
) {
}
