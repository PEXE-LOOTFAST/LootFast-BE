package com.lootfast.backend.merchant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * A store's payout account (FR-ME-03). Source of truth for the VietQR string
 * generated per order (BR-PAY-02): the money moves directly buyer → merchant,
 * so the backend never touches it — it only records where transfers should go.
 *
 * <p>One account per merchant, enforced by the unique constraint on
 * {@code merchant_profile_id} and the {@link OneToOne} mapping.
 */
@Entity
@Table(
        name = "merchant_bank_accounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_merchant_bank_accounts_merchant",
                columnNames = "merchant_profile_id"
        )
)
public class MerchantBankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_profile_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MerchantProfile merchant;

    // 6-digit NAPAS acquirer BIN: canonical bank id used to build the VietQR.
    @Column(name = "bank_bin", nullable = false, length = 6)
    private String bankBin;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "account_number", nullable = false, length = 19)
    private String accountNumber;

    @Column(name = "account_holder_name", nullable = false, length = 120)
    private String accountHolderName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MerchantBankAccount() {
    }

    public MerchantBankAccount(
            MerchantProfile merchant,
            String bankBin,
            String bankName,
            String accountNumber,
            String accountHolderName
    ) {
        this.merchant = merchant;
        this.bankBin = bankBin;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountHolderName = accountHolderName;
    }

    public void update(
            String bankBin,
            String bankName,
            String accountNumber,
            String accountHolderName
    ) {
        this.bankBin = bankBin;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountHolderName = accountHolderName;
    }

    @PrePersist
    private void beforeInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public MerchantProfile getMerchant() {
        return merchant;
    }

    public String getBankBin() {
        return bankBin;
    }

    public String getBankName() {
        return bankName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
