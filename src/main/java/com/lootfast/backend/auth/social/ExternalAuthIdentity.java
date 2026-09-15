package com.lootfast.backend.auth.social;

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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(
        name = "external_auth_identities",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_external_auth_provider_subject",
                        columnNames = {"provider", "provider_subject"}
                ),
                @UniqueConstraint(
                        name = "uk_external_auth_user_provider",
                        columnNames = {"user_id", "provider"}
                )
        }
)
public class ExternalAuthIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16, updatable = false)
    private SocialProvider provider;

    @Column(name = "provider_subject", nullable = false, length = 255, updatable = false)
    private String providerSubject;

    @Column(name = "provider_email", nullable = false, length = 255)
    private String providerEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    protected ExternalAuthIdentity() {
    }

    public ExternalAuthIdentity(
            User user,
            SocialProvider provider,
            String providerSubject,
            String providerEmail,
            Instant now
    ) {
        this.user = user;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.providerEmail = providerEmail;
        this.createdAt = now;
        this.lastLoginAt = now;
    }

    public User getUser() {
        return user;
    }

    public void recordLogin(String email, Instant now) {
        this.providerEmail = email;
        this.lastLoginAt = now;
    }
}
