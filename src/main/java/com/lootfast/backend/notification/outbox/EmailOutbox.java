package com.lootfast.backend.notification.outbox;

import com.lootfast.backend.notification.entity.UserNotification;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "email_outbox")
public class EmailOutbox {
  @Id private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "notification_id",
      referencedColumnName = "public_id",
      nullable = false,
      unique = true,
      updatable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private UserNotification notification;

  @Column(name = "recipient_email", nullable = false, length = 320, updatable = false)
  private String recipientEmail;

  @Column(nullable = false, length = 200, updatable = false)
  private String subject;

  @Column(name = "html_body", nullable = false, columnDefinition = "text", updatable = false)
  private String htmlBody;

  @Enumerated(EnumType.STRING)
  @Column(name = "preference_category", nullable = false, length = 32, updatable = false)
  private EmailPreferenceCategory preferenceCategory;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "lock_token")
  private UUID lockToken;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "last_error_code", length = 64)
  private String lastErrorCode;

  protected EmailOutbox() {}

  public EmailOutbox(
      UserNotification notification,
      String email,
      String subject,
      String body,
      EmailPreferenceCategory category,
      Instant now) {
    id = notification.getPublicId();
    this.notification = notification;
    recipientEmail = email;
    this.subject = subject;
    htmlBody = body;
    preferenceCategory = category;
    status = "PENDING";
    nextAttemptAt = now;
    createdAt = now;
  }
}
