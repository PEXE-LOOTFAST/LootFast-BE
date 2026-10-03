package com.lootfast.backend.support.scheduler;

import com.lootfast.backend.support.service.PaymentDisputeService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Quét các đơn chuyển khoản còn treo quá 24h sau khi kết thúc, tự mở ticket tranh chấp và nhắc cửa
 * hàng xác nhận (task 045). Mỗi đơn xử lý trong transaction riêng nên một lỗi không chặn cả lô. Tắt
 * trong test bằng {@code lootfast.payment-dispute-sweeper.enabled=false}.
 */
@Component
@ConditionalOnProperty(
    name = "lootfast.payment-dispute-sweeper.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class UnconfirmedTransferSweeper {
  private static final Logger log = LoggerFactory.getLogger(UnconfirmedTransferSweeper.class);
  private final PaymentDisputeService disputes;

  public UnconfirmedTransferSweeper(PaymentDisputeService disputes) {
    this.disputes = disputes;
  }

  @Scheduled(fixedDelayString = "PT15M")
  public void sweep() {
    try {
      for (UUID orderId : disputes.findCandidates()) {
        try {
          disputes.openDisputeFor(orderId);
        } catch (Exception e) {
          log.warn("Auto payment-dispute failed for order {}", orderId, e);
        }
      }
    } catch (Exception e) {
      log.warn("Payment-dispute sweep could not list candidates", e);
    }
  }
}
