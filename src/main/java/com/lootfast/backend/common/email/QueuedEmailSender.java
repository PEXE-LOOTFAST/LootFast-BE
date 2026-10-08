package com.lootfast.backend.common.email;

import java.util.UUID;

/** Synchronous single-attempt transport. The outbox owns scheduling, retries and persistence. */
public interface QueuedEmailSender {
  EmailDeliveryResult sendQueuedEmail(
      String to, UUID idempotencyKey, String subject, String htmlBody);
}
