package com.lootfast.backend.premium.vnpay.query;

import java.time.Instant;
import java.util.UUID;

public record QueryJob(
    UUID orderId, UUID token, String reference, Instant transactionDate, int attempt) {}
