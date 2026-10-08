package com.lootfast.backend.support.entity;

import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.order.entity.CustomerOrder;
import com.lootfast.backend.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "support_tickets",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_support_tickets_public", columnNames = "public_id"))
public class SupportTicket {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, updatable = false)
  private UUID publicId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reporter_id", updatable = false)
  private User reporter;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16, updatable = false)
  private TicketOrigin origin;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24, updatable = false)
  private TicketCategory category;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", updatable = false)
  private CustomerOrder order;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "merchant_profile_id", updatable = false)
  private MerchantProfile merchant;

  @Column(nullable = false, length = 160, updatable = false)
  private String title;

  @Column(nullable = false, length = 2000, updatable = false)
  private String description;

  @Column(name = "evidence_image_url", length = 2048)
  private String evidenceImageUrl;

  @Column(name = "evidence_storage_key", length = 255)
  private String evidenceStorageKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private TicketStatus status;

  @Column(name = "resolution_note", length = 2000)
  private String resolutionNote;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected SupportTicket() {}

  public SupportTicket(
      User reporter,
      TicketOrigin origin,
      TicketCategory category,
      CustomerOrder order,
      MerchantProfile merchant,
      String title,
      String description,
      Instant now) {
    this.publicId = UUID.randomUUID();
    this.reporter = reporter;
    this.origin = origin;
    this.category = category;
    this.order = order;
    this.merchant = merchant;
    this.title = title;
    this.description = description;
    this.status = TicketStatus.OPEN;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void attachEvidence(String url, String storageKey, Instant now) {
    this.evidenceImageUrl = url;
    this.evidenceStorageKey = storageKey;
    this.updatedAt = now;
  }

  /** Cửa duy nhất đổi trạng thái; chặn bước nhảy không hợp lệ theo {@link TicketStatus}. */
  private void applyStatus(TicketStatus next, Instant now) {
    if (!status.canTransitionTo(next))
      throw new IllegalStateException("Cannot move ticket from " + status + " to " + next);
    this.status = next;
    this.updatedAt = now;
  }

  public void markInReview(Instant now) {
    applyStatus(TicketStatus.IN_REVIEW, now);
  }

  public void resolve(String note, Instant now) {
    applyStatus(TicketStatus.RESOLVED, now);
    this.resolutionNote = note;
  }

  public void reject(String note, Instant now) {
    applyStatus(TicketStatus.REJECTED, now);
    this.resolutionNote = note;
  }

  public void close(Instant now) {
    applyStatus(TicketStatus.CLOSED, now);
  }

  public Long getId() {
    return id;
  }

  public UUID getPublicId() {
    return publicId;
  }

  public User getReporter() {
    return reporter;
  }

  public TicketOrigin getOrigin() {
    return origin;
  }

  public TicketCategory getCategory() {
    return category;
  }

  public CustomerOrder getOrder() {
    return order;
  }

  public MerchantProfile getMerchant() {
    return merchant;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getEvidenceImageUrl() {
    return evidenceImageUrl;
  }

  public TicketStatus getStatus() {
    return status;
  }

  public String getResolutionNote() {
    return resolutionNote;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
