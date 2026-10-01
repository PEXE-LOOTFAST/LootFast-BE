package com.lootfast.backend.order.dto;

import java.math.BigDecimal;

public record OrderImpactSummaryResponse(
    long completedOrders, long rescuedPortions, BigDecimal totalSavings) {}
