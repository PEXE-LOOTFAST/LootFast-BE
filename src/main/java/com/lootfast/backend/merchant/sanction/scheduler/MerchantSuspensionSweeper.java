package com.lootfast.backend.merchant.sanction.scheduler;

import com.lootfast.backend.merchant.sanction.service.MerchantSanctionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dọn cờ đình chỉ đã hết thời hạn để cửa hàng trở lại feed. Các lớp kiểm tra khác đã dùng
 * {@code suspended_until > now} nên hết hạn có hiệu lực ngay; sweeper chỉ giữ dữ liệu sạch. Tắt trong
 * test bằng {@code lootfast.suspension-sweeper.enabled=false}.
 */
@Component
@ConditionalOnProperty(
    name = "lootfast.suspension-sweeper.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MerchantSuspensionSweeper {
  private static final Logger log = LoggerFactory.getLogger(MerchantSuspensionSweeper.class);
  private final MerchantSanctionService sanctions;

  public MerchantSuspensionSweeper(MerchantSanctionService sanctions) {
    this.sanctions = sanctions;
  }

  @Scheduled(fixedDelayString = "PT5M")
  public void sweep() {
    try {
      int lifted = sanctions.clearExpiredSuspensions();
      if (lifted > 0) log.info("Lifted {} expired merchant suspensions", lifted);
    } catch (Exception e) {
      log.warn("Merchant suspension sweep failed", e);
    }
  }
}
