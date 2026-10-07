package com.lootfast.backend.notification.outbox;

import java.util.UUID;

public record ClaimedEmail(
    UUID id, UUID token, String recipient, String subject, String htmlBody, int attempt) {
  @Override
  public String toString() {
    return "ClaimedEmail[id=" + id + ", attempt=" + attempt + "]";
  }
}
