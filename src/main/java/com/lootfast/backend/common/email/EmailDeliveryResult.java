package com.lootfast.backend.common.email;

import java.time.Duration;

/** Provider acceptance is not proof of delivery to an inbox. Never includes provider response bodies. */
public record EmailDeliveryResult(Outcome outcome, String errorCode, Duration retryAfter) {
    public enum Outcome { ACCEPTED, RETRYABLE, PERMANENT_FAILURE }
    public static EmailDeliveryResult accepted() { return new EmailDeliveryResult(Outcome.ACCEPTED, null, Duration.ZERO); }
    public static EmailDeliveryResult retry(String code, Duration delay) { return new EmailDeliveryResult(Outcome.RETRYABLE, code, delay); }
    public static EmailDeliveryResult failed(String code) { return new EmailDeliveryResult(Outcome.PERMANENT_FAILURE, code, Duration.ZERO); }
}
