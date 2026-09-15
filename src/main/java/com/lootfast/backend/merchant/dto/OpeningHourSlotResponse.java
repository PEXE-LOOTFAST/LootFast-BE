package com.lootfast.backend.merchant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lootfast.backend.merchant.entity.MerchantOpeningHour;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record OpeningHourSlotResponse(
        DayOfWeek dayOfWeek,
        @JsonFormat(pattern = "HH:mm") LocalTime opensAt,
        @JsonFormat(pattern = "HH:mm") LocalTime closesAt
) {
    public static OpeningHourSlotResponse from(MerchantOpeningHour openingHour) {
        return new OpeningHourSlotResponse(
                openingHour.getDayOfWeek(),
                openingHour.getOpensAt(),
                openingHour.getClosesAt()
        );
    }
}
