package com.lootfast.backend.payment.entity;

import com.lootfast.backend.order.entity.CustomerOrder;
import com.lootfast.backend.order.entity.PaymentMethod;
import com.lootfast.backend.order.entity.PaymentStatus;
import com.lootfast.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_payment_events")
public class PaymentEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true, updatable = false)
  private UUID publicId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false, updatable = false)
  private CustomerOrder order;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "actor_user_id", nullable = false, updatable = false)
  private User actor;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 32, updatable = false)
  private PaymentEventType eventType;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method", nullable = false, length = 24, updatable = false)
  private PaymentMethod paymentMethod;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", nullable = false, length = 24, updatable = false)
  private PaymentStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false, length = 24, updatable = false)
  private PaymentStatus toStatus;

  @Column(length = 500, updatable = false)
  private String note;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected PaymentEvent() {}

  public PaymentEvent(
      CustomerOrder order,
      User actor,
      PaymentEventType eventType,
      PaymentStatus fromStatus,
      PaymentStatus toStatus,
      String note,
      Instant createdAt) {
    this.publicId = UUID.randomUUID();
    this.order = order;
    this.actor = actor;
    this.eventType = eventType;
    this.paymentMethod = order.getPaymentMethod();
    this.fromStatus = fromStatus;
    this.toStatus = toStatus;
    this.note = note;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public UUID getPublicId() {
    return publicId;
  }

  public CustomerOrder getOrder() {
    return order;
  }

  public User getActor() {
    return actor;
  }

  public PaymentEventType getEventType() {
    return eventType;
  }

  public PaymentMethod getPaymentMethod() {
    return paymentMethod;
  }

  public PaymentStatus getFromStatus() {
    return fromStatus;
  }

  public PaymentStatus getToStatus() {
    return toStatus;
  }

  public String getNote() {
    return note;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
