package com.lootfast.backend.payment.dto;

import com.lootfast.backend.order.entity.PaymentMethod;
import java.math.BigDecimal;

public record PaymentInstructions(
    PaymentMethod method,
    String state,
    BigDecimal amount,
    String reference,
    BankTransfer bank,
    String qrImageUrl,
    String message) {
  public record BankTransfer(
      String bankBin, String bankName, String accountNumber, String accountHolderName) {}
}
