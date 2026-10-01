package com.lootfast.backend.common.email;

import java.math.BigDecimal;
import java.util.UUID;

public interface EmailSender {
    void sendOrderNotification(String to, UUID notificationId, String title, String message);

    void sendSignupOtp(String to, String otp);

    void sendPasswordResetOtp(String to, String otp);

    void sendMatchingDeal(
            String to,
            UUID notificationId,
            String dealName,
            String storeName,
            BigDecimal originalPrice,
            BigDecimal discountedPrice
    );
}
