package com.lootfast.backend.payment.service;

import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.order.dto.OrderResponse;
import com.lootfast.backend.order.entity.CustomerOrder;
import com.lootfast.backend.order.entity.OrderStatus;
import com.lootfast.backend.order.entity.PaymentMethod;
import com.lootfast.backend.order.entity.PaymentStatus;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.order.service.OrderService;
import com.lootfast.backend.payment.dto.PaymentInstructions;
import com.lootfast.backend.payment.entity.PaymentEvent;
import com.lootfast.backend.payment.entity.PaymentEventType;
import com.lootfast.backend.payment.repository.PaymentEventRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Database operations for bank-transfer and cash payment instructions. */
@Service
public class PaymentTransactionService {
  private final com.lootfast.backend.config.DatabaseLockTimeout lockTimeout;
  private final OrderRepository orders;
  private final PaymentEventRepository events;
  private final Clock clock;

  public PaymentTransactionService(
      OrderRepository orders,
      PaymentEventRepository events,
      Clock clock,
      com.lootfast.backend.config.DatabaseLockTimeout lockTimeout) {
    this.lockTimeout = lockTimeout;
    this.orders = orders;
    this.events = events;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PaymentInstructions instructions(String subject, UUID orderId) {
    CustomerOrder order =
        orders
            .findByPublicIdAndBuyerId(orderId, OrderService.userId(subject))
            .orElseThrow(
                () -> OrderService.error(ErrorCode.ORDER_NOT_FOUND, "Order was not found"));
    return view(order);
  }

  @Transactional(timeout = 5)
  public OrderResponse changeMethod(String subject, UUID orderId, PaymentMethod method) {
    lockTimeout.apply();
    CustomerOrder order = owned(subject, orderId);
    if (method == null
        || order.getStatus() != OrderStatus.RESERVED
        || order.getPaymentStatus() != PaymentStatus.UNPAID) {
      throw OrderService.error(ErrorCode.PAYMENT_STATE_CONFLICT, "Payment method cannot change");
    }
    if (method == PaymentMethod.BANK_TRANSFER && order.getBankBin() == null) {
      throw OrderService.error(
          ErrorCode.BANK_ACCOUNT_NOT_FOUND,
          "No bank account was configured when this order was created");
    }
    order.changePaymentMethod(method);
    return OrderResponse.from(order);
  }

  @Transactional(timeout = 5)
  public OrderResponse reportBankTransfer(String subject, UUID orderId) {
    lockTimeout.apply();
    CustomerOrder order = owned(subject, orderId);
    if (order.getPaymentMethod() != PaymentMethod.BANK_TRANSFER
        || order.getStatus() != OrderStatus.RESERVED
        || order.getPaymentStatus() == PaymentStatus.CONFIRMED) {
      throw OrderService.error(
          ErrorCode.PAYMENT_STATE_CONFLICT,
          "Only an unpaid reserved bank transfer can be reported");
    }
    PaymentStatus previous = order.getPaymentStatus();
    var now = clock.instant();
    if (order.reportBankTransfer(now)) {
      events.save(
          new PaymentEvent(
              order,
              order.getBuyer(),
              PaymentEventType.TRANSFER_REPORTED,
              previous,
              PaymentStatus.REVIEW_REQUIRED,
              null,
              now));
    }
    return OrderResponse.from(order);
  }

  private CustomerOrder owned(String subject, UUID orderId) {
    CustomerOrder order =
        orders
            .findForUpdate(orderId)
            .orElseThrow(
                () -> OrderService.error(ErrorCode.ORDER_NOT_FOUND, "Order was not found"));
    if (!order.getBuyer().getId().equals(OrderService.userId(subject))) {
      throw OrderService.error(ErrorCode.ORDER_NOT_FOUND, "Order was not found");
    }
    if (order.getBuyer().getStatus() != com.lootfast.backend.user.entity.UserStatus.VERIFIED
        || !order.getBuyer().hasRole(com.lootfast.backend.user.entity.UserRole.BUYER)) {
      throw OrderService.error(ErrorCode.ACCESS_DENIED, "Verified buyer is required");
    }
    return order;
  }

  private PaymentInstructions view(CustomerOrder order) {
    var bank =
        order.getBankBin() == null
            ? null
            : new PaymentInstructions.BankTransfer(
                order.getBankBin(),
                order.getBankName(),
                order.getBankAccountNumber(),
                order.getBankHolderName());
    String qr = null;
    if (order.getPaymentMethod() == PaymentMethod.BANK_TRANSFER
        && bank != null
        && order.getStatus() == OrderStatus.RESERVED
        && order.getPaymentStatus() == PaymentStatus.UNPAID) {
      try {
        long amount = order.getTotalAmount().longValueExact();
        qr =
            "https://img.vietqr.io/image/"
                + bank.bankBin()
                + "-"
                + bank.accountNumber()
                + "-compact2.png?amount="
                + amount
                + "&addInfo="
                + encode(order.getPaymentReference())
                + "&accountName="
                + encode(bank.accountHolderName());
      } catch (ArithmeticException ignored) {
        // Manual transfer remains available for unsupported QR amounts.
      }
    }
    String message =
        order.getPaymentMethod() == PaymentMethod.CASH
            ? "Pay the store when collecting your order."
            : "If the QR image cannot load, copy the bank details, exact amount and reference."
                + " The store confirms receipt.";
    return new PaymentInstructions(
        order.getPaymentMethod(),
        order.getPaymentStatus().name(),
        order.getTotalAmount(),
        order.getPaymentReference(),
        bank,
        qr,
        message);
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
