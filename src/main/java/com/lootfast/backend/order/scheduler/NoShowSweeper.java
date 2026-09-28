package com.lootfast.backend.order.scheduler;

import com.lootfast.backend.order.service.OrderService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sweeps reserved orders left uncollected 30 minutes past pickup end into NO_SHOW and returns their
 * held stock. Runs on a separate bean so each order is finalized in its own transaction and one
 * failure never rolls back the whole batch. Disabled in tests via {@code
 * lootfast.no-show-sweeper.enabled=false}.
 */
@Component
@ConditionalOnProperty(
    name = "lootfast.no-show-sweeper.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class NoShowSweeper {
  private static final Logger log = LoggerFactory.getLogger(NoShowSweeper.class);
  private final OrderService orders;

  public NoShowSweeper(OrderService orders) {
    this.orders = orders;
  }

  @Scheduled(fixedDelayString = "PT5M")
  public void sweep() {
    try {
      for (UUID orderId : orders.findNoShowCandidates()) {
        try {
          orders.markNoShow(orderId);
        } catch (Exception e) {
          log.warn("No-show sweep failed for order {}", orderId, e);
        }
      }
    } catch (Exception e) {
      log.warn("No-show sweep could not list candidates", e);
    }
  }
}
