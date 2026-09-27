package com.lootfast.backend.order.dto;

import com.lootfast.backend.order.entity.PaymentMethod;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record CreateOrderRequest(
    @NotNull UUID dealId, @Min(1) @Max(5) int quantity, @NotNull PaymentMethod paymentMethod) {}
