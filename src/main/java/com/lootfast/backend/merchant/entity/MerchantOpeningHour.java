package com.lootfast.backend.merchant.entity;

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

import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(
        name = "merchant_opening_hours",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_merchant_opening_hours_slot",
                columnNames = {"merchant_profile_id", "day_of_week", "opens_at", "closes_at"}
        )
)
public class MerchantOpeningHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_profile_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MerchantProfile merchantProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 9)
    private DayOfWeek dayOfWeek;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    protected MerchantOpeningHour() {
    }

    public MerchantOpeningHour(
            MerchantProfile merchantProfile,
            DayOfWeek dayOfWeek,
            LocalTime opensAt,
            LocalTime closesAt
    ) {
        this.merchantProfile = merchantProfile;
        this.dayOfWeek = dayOfWeek;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getOpensAt() {
        return opensAt;
    }

    public LocalTime getClosesAt() {
        return closesAt;
    }
}
