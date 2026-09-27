package com.lootfast.backend.order.entity;

import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.user.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "customer_orders",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_orders_public", columnNames = "public_id"),
      @UniqueConstraint(
          name = "uk_orders_key",
          columnNames = {"buyer_id", "reservation_key"}),
      @UniqueConstraint(name = "uk_orders_reference", columnNames = "payment_reference")
    })
public class CustomerOrder {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, updatable = false)
  private UUID publicId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "buyer_id", updatable = false)
  private User buyer;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "merchant_profile_id", updatable = false)
  private MerchantProfile merchant;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "deal_id", updatable = false)
  private Deal deal;

  @Column(name = "deal_name", nullable = false, length = 160, updatable = false)
  private String dealName;

  @Column(nullable = false, updatable = false)
  private int quantity;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2, updatable = false)
  private BigDecimal unitPrice;

  @Column(name = "total_amount", nullable = false, precision = 14, scale = 2, updatable = false)
  private BigDecimal totalAmount;

  @Column(name = "pickup_start", nullable = false, updatable = false)
  private Instant pickupStart;

  @Column(name = "pickup_end", nullable = false, updatable = false)
  private Instant pickupEnd;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private OrderStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method", nullable = false, length = 24)
  private PaymentMethod paymentMethod;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_status", nullable = false, length = 24)
  private PaymentStatus paymentStatus;

  @Column(name = "payment_reference", nullable = false, length = 23, updatable = false)
  private String paymentReference;

  @Column(name = "reservation_key", nullable = false, updatable = false)
  private UUID reservationKey;

  @Column(name = "request_fingerprint", nullable = false, length = 200, updatable = false)
  private String requestFingerprint;

  @Column(name = "replay_until", nullable = false, updatable = false)
  private Instant replayUntil;

  @Column(name = "creation_response", nullable = false, columnDefinition = "text")
  private String creationResponse;

  @Column(name = "bank_bin", length = 6, updatable = false)
  private String bankBin;

  @Column(name = "bank_name", length = 100, updatable = false)
  private String bankName;

  @Column(name = "bank_account_number", length = 19, updatable = false)
  private String bankAccountNumber;

  @Column(name = "bank_holder_name", length = 120, updatable = false)
  private String bankHolderName;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected CustomerOrder() {}

  public CustomerOrder(
      User buyer,
      Deal deal,
      int quantity,
      PaymentMethod method,
      UUID key,
      String fingerprint,
      Instant now) {
    this.publicId = UUID.randomUUID();
    this.buyer = buyer;
    this.deal = deal;
    this.merchant = deal.getMerchant();
    this.dealName = deal.getName();
    this.quantity = quantity;
    this.unitPrice = deal.getDiscountedPrice();
    this.totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
    this.pickupStart = deal.getPickupStart();
    this.pickupEnd = deal.getPickupEnd();
    this.status = OrderStatus.RESERVED;
    this.paymentStatus = PaymentStatus.UNPAID;
    this.paymentMethod = method;
    this.reservationKey = key;
    this.requestFingerprint = fingerprint;
    this.createdAt = now;
    this.replayUntil = now.plusSeconds(86400);
    this.paymentReference =
        "LF-"
            + publicId
                .toString()
                .replace("-", "")
                .substring(0, 20)
                .toUpperCase(java.util.Locale.ROOT);
    this.creationResponse = "";
  }

  public void snapshotBank(String bin, String name, String number, String holder) {
    bankBin = bin;
    bankName = name;
    bankAccountNumber = number;
    bankHolderName = holder;
  }

  public void setCreationResponse(String json) {
    creationResponse = json;
  }

  public void requirePaymentReview() {
    paymentStatus = PaymentStatus.REVIEW_REQUIRED;
  }

  public void changePaymentMethod(PaymentMethod method) {
    paymentMethod = method;
  }

  public Long getId() {
    return id;
  }

  public UUID getPublicId() {
    return publicId;
  }

  public User getBuyer() {
    return buyer;
  }

  public Deal getDeal() {
    return deal;
  }

  public MerchantProfile getMerchant() {
    return merchant;
  }

  public String getDealName() {
    return dealName;
  }

  public int getQuantity() {
    return quantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public Instant getPickupStart() {
    return pickupStart;
  }

  public Instant getPickupEnd() {
    return pickupEnd;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public PaymentMethod getPaymentMethod() {
    return paymentMethod;
  }

  public PaymentStatus getPaymentStatus() {
    return paymentStatus;
  }

  public String getPaymentReference() {
    return paymentReference;
  }

  public String getRequestFingerprint() {
    return requestFingerprint;
  }

  public Instant getReplayUntil() {
    return replayUntil;
  }

  public String getCreationResponse() {
    return creationResponse;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getBankBin() {
    return bankBin;
  }

  public String getBankName() {
    return bankName;
  }

  public String getBankAccountNumber() {
    return bankAccountNumber;
  }

  public String getBankHolderName() {
    return bankHolderName;
  }
}
