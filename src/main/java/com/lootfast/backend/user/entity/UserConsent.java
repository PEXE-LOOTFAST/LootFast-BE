package com.lootfast.backend.user.entity;

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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(
        name = "user_consents",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_consents_user_document_version",
                columnNames = {"user_id", "document", "document_version"}
        )
)
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "document", nullable = false, length = 32, updatable = false)
    private ConsentDocument document;

    @Column(name = "document_version", nullable = false, length = 50, updatable = false)
    private String documentVersion;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    protected UserConsent() {
    }

    public UserConsent(
            User user,
            ConsentDocument document,
            String documentVersion,
            Instant acceptedAt
    ) {
        this.user = user;
        this.document = document;
        this.documentVersion = documentVersion;
        this.acceptedAt = acceptedAt;
    }

    public ConsentDocument getDocument() {
        return document;
    }

    public String getDocumentVersion() {
        return documentVersion;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }
}
