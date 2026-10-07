package com.lootfast.backend.premium.vnpay.query;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "lootfast.vnpay.query.enabled", havingValue = "true")
public class QueryWorker {
  private static final Logger log = LoggerFactory.getLogger(QueryWorker.class);
  private final QueryStore store;
  private final QueryClient client;
  private final MeterRegistry metrics;

  public QueryWorker(QueryStore store, QueryClient client, MeterRegistry metrics) {
    this.store = store;
    this.client = client;
    this.metrics = metrics;
  }

  @Scheduled(
      fixedDelayString = "PT1M",
      initialDelayString = "PT1M",
      scheduler = "vnpayQueryScheduler")
  public void sweep() {
    try {
      for (int i = 0; i < 5; i++) {
        var job = store.claim();
        if (job.isEmpty()) break;
        var result = client.query(job.get());
        String outcome = store.finish(job.get(), result);
        metrics.counter("lootfast.vnpay.query", "result", outcome).increment();
        log.info("Premium reconciliation order={} result={}", job.get().orderId(), outcome);
      }
    } catch (RuntimeException e) {
      metrics.counter("lootfast.vnpay.query.errors").increment();
      log.error("Premium reconciliation failed; exceptionType={}", e.getClass().getSimpleName());
    }
  }
}
