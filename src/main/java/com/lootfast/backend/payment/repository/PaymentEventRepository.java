package com.lootfast.backend.payment.repository;

import com.lootfast.backend.payment.entity.PaymentEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {
  long countByOrderPublicId(UUID orderId);
}
