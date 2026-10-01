package com.lootfast.backend.order.entity;

import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.user.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
    name = "customer_orders",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_orders_public", columnNames = "public_id"),
      @UniqueConstraint(
          name = "uk_orders_key",
          columnNames = {"buyer_id", "reservation_key"}),
      @UniqueConstraint(name = "uk_orders_reference", columnNames = "payment_reference"),
      @UniqueConstraint(name = "uk_orders_pickup_code", columnNames = "pickup_code")
    })
public class CustomerOrder {
  private static final SecureRandom PICKUP_CODE_RANDOM = new SecureRandom();
  private static final char[] PICKUP_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

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

  @Column(
      name = "original_unit_price",
      nullable = false,
      precision = 12,
      scale = 2,
      updatable = false)
  private BigDecimal originalUnitPrice;

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

  @Column(name = "pickup_code", nullable = false, length = 9, updatable = false)
  private String pickupCode;

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

  @Column(name = "payment_reported_at")
  private Instant paymentReportedAt;

  @Column(name = "payment_confirmed_at")
  private Instant paymentConfirmedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payment_confirmed_by_user_id")
  private User paymentConfirmedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "finalized_at")
  private Instant finalizedAt;

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
    this.originalUnitPrice = deal.getOriginalPrice();
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
                .toUpperCase(Locale.ROOT);
    this.pickupCode = generatePickupCode();
    this.creationResponse = "";
  }

  private static String generatePickupCode() {
    StringBuilder value = new StringBuilder("LF-");
    for (int index = 0; index < 6; index++) {
      value.append(PICKUP_CODE_ALPHABET[PICKUP_CODE_RANDOM.nextInt(PICKUP_CODE_ALPHABET.length)]);
    }
    return value.toString();
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

  public boolean reportBankTransfer(Instant now) {
    if (paymentMethod != PaymentMethod.BANK_TRANSFER || status != OrderStatus.RESERVED) {
      throw new IllegalStateException("Only reserved bank-transfer orders can be reported");
    }
    if (paymentStatus == PaymentStatus.CONFIRMED) {
      throw new IllegalStateException("Confirmed payments cannot be reported again");
    }
    if (paymentStatus == PaymentStatus.REVIEW_REQUIRED) {
      return false;
    }
    paymentStatus = PaymentStatus.REVIEW_REQUIRED;
    paymentReportedAt = now;
    return true;
  }

  public boolean confirmBankTransfer(User actor, Instant now) {
    if (paymentMethod != PaymentMethod.BANK_TRANSFER || status != OrderStatus.RESERVED) {
      throw new IllegalStateException("Only reserved bank-transfer orders can be confirmed");
    }
    if (paymentStatus == PaymentStatus.CONFIRMED) {
      return false;
    }
    paymentStatus = PaymentStatus.CONFIRMED;
    paymentConfirmedAt = now;
    paymentConfirmedBy = actor;
    return true;
  }

  public void rejectBankTransfer() {
    if (paymentMethod != PaymentMethod.BANK_TRANSFER
        || status != OrderStatus.RESERVED
        || paymentStatus != PaymentStatus.REVIEW_REQUIRED) {
      throw new IllegalStateException("Only reported bank transfers can be rejected");
    }
    paymentStatus = PaymentStatus.UNPAID;
    paymentReportedAt = null;
  }

  public void collectCash(User actor, Instant now) {
    if (paymentMethod != PaymentMethod.CASH
        || status != OrderStatus.RESERVED
        || paymentStatus != PaymentStatus.UNPAID) {
      throw new IllegalStateException("Cash can only be collected for an unpaid reserved order");
    }
    paymentStatus = PaymentStatus.CONFIRMED;
    paymentConfirmedAt = now;
    paymentConfirmedBy = actor;
  }

  /**
   * Cửa duy nhất để đổi trạng thái đơn. Chặn mọi bước nhảy không hợp lệ theo bảng {@link
   * OrderStatus#canTransitionTo} và đóng dấu thời điểm chốt khi vào trạng thái cuối. Các check nghiệp
   * vụ (giờ huỷ, quyền sở hữu…) nằm ở tầng service; đây là lớp bất biến cuối cùng.
   */
  private void transition(OrderStatus next, Instant now) {
    if (!status.canTransitionTo(next))
      throw new IllegalStateException("Cannot move order from " + status + " to " + next);
    status = next;
    if (next.isTerminal()) finalizedAt = now;
  }

  public void cancel(Instant now) {
    transition(OrderStatus.CANCELLED, now);
  }

  public void markNoShow(Instant now) {
    transition(OrderStatus.NO_SHOW, now);
  }

  public void complete(Instant now) {
    transition(OrderStatus.COMPLETED, now);
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

  public BigDecimal getOriginalUnitPrice() {
    return originalUnitPrice;
  }

  /** Số tiền khách tiết kiệm được so với giá gốc: (giá gốc − giá đã giảm) × số lượng. */
  public BigDecimal getSavings() {
    return originalUnitPrice.subtract(unitPrice).multiply(BigDecimal.valueOf(quantity));
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

  public String getPickupCode() {
    return pickupCode;
  }

  public Instant getFinalizedAt() {
    return finalizedAt;
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

  public Instant getPaymentReportedAt() {
    return paymentReportedAt;
  }

  public Instant getPaymentConfirmedAt() {
    return paymentConfirmedAt;
  }

  public User getPaymentConfirmedBy() {
    return paymentConfirmedBy;
  }
}
