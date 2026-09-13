package com.lootfast.backend.merchant.application.entity;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "merchant_applications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_merchant_applications_user_id",
                columnNames = "user_id"
        )
)
public class MerchantApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "business_license_number", nullable = false, length = 14)
    private String businessLicenseNumber;

    @Column(name = "license_storage_key", length = 255)
    private String licenseStorageKey;

    @Column(name = "license_format", length = 16)
    private String licenseFormat;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MerchantApplicationStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MerchantApplication() {
    }

    public MerchantApplication(User user, String businessLicenseNumber, UUID submissionId) {
        this.user = user;
        startUpload(businessLicenseNumber, submissionId);
    }

    @PrePersist
    private void beforeInsert() {
        Instant now = Instant.now();
        publicId = UUID.randomUUID();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public void startUpload(String licenseNumber, UUID newSubmissionId) {
        businessLicenseNumber = licenseNumber;
        submissionId = newSubmissionId;
        status = MerchantApplicationStatus.UPLOADING;
        licenseStorageKey = null;
        licenseFormat = null;
        submittedAt = null;
        reviewedAt = null;
        reviewedBy = null;
        rejectionReason = null;
    }

    public void markPending(String storageKey, String format, Instant now) {
        licenseStorageKey = storageKey;
        licenseFormat = format;
        status = MerchantApplicationStatus.PENDING;
        submittedAt = now;
    }

    public void markUploadFailed() {
        status = MerchantApplicationStatus.UPLOAD_FAILED;
    }

    public void approve(User reviewer, Instant now) {
        status = MerchantApplicationStatus.APPROVED;
        reviewedBy = reviewer;
        reviewedAt = now;
        rejectionReason = null;
    }

    public void reject(User reviewer, String reason, Instant now) {
        status = MerchantApplicationStatus.REJECTED;
        reviewedBy = reviewer;
        reviewedAt = now;
        rejectionReason = reason;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public User getUser() {
        return user;
    }

    public String getBusinessLicenseNumber() {
        return businessLicenseNumber;
    }

    public String getLicenseStorageKey() {
        return licenseStorageKey;
    }

    public String getLicenseFormat() {
        return licenseFormat;
    }

    public UUID getSubmissionId() {
        return submissionId;
    }

    public MerchantApplicationStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public User getReviewedBy() {
        return reviewedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
