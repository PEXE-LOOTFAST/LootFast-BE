package com.lootfast.backend.merchant.dto;

import java.util.List;

public record MerchantOpeningHoursResponse(
        List<OpeningHourSlotResponse> slots
) {
    public MerchantOpeningHoursResponse {
        slots = List.copyOf(slots);
    }
}
