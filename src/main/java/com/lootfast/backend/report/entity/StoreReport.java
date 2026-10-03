package com.lootfast.backend.report.entity;

import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.order.entity.CustomerOrder;
import com.lootfast.backend.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "store_reports",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_store_reports_public", columnNames = "public_id"),
      @UniqueConstraint(
          name = "uk_store_reports_order_reporter_type",
          columnNames = {"order_id", "reporter_id", "report_type"})
    })
public class StoreReport {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, updatable = false)
  private UUID publicId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reporter_id", updatable = false)
  private User reporter;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "merchant_profile_id", updatable = false)
  private MerchantProfile merchant;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", updatable = false)
  private CustomerOrder order;

  @Enumerated(EnumType.STRING)
  @Column(name = "report_type", nullable = false, length = 24, updatable = false)
  private ReportType reportType;

  @Column(nullable = false, length = 1000, updatable = false)
  private String description;

  @Column(name = "evidence_image_url", length = 2048)
  private String evidenceImageUrl;

  @Column(name = "evidence_storage_key", length = 255)
  private String evidenceStorageKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ReportStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected StoreReport() {}

  public StoreReport(
      User reporter,
      MerchantProfile merchant,
      CustomerOrder order,
      ReportType reportType,
      String description,
      Instant now) {
    this.publicId = UUID.randomUUID();
    this.reporter = reporter;
    this.merchant = merchant;
    this.order = order;
    this.reportType = reportType;
    this.description = description;
    this.status = ReportStatus.OPEN;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void attachEvidence(String url, String storageKey, Instant now) {
    this.evidenceImageUrl = url;
    this.evidenceStorageKey = storageKey;
    this.updatedAt = now;
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

  public MerchantProfile getMerchant() {
    return merchant;
  }

  public CustomerOrder getOrder() {
    return order;
  }

  public ReportType getReportType() {
    return reportType;
  }

  public String getDescription() {
    return description;
  }

  public String getEvidenceImageUrl() {
    return evidenceImageUrl;
  }

  public String getEvidenceStorageKey() {
    return evidenceStorageKey;
  }

  public ReportStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
