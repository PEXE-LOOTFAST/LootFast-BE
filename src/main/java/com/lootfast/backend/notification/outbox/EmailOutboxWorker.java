package com.lootfast.backend.notification.outbox;

import com.lootfast.backend.common.email.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;

@Component
@ConditionalOnProperty(name="lootfast.email-outbox.enabled", havingValue="true", matchIfMissing=true)
public class EmailOutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(EmailOutboxWorker.class);
    private final EmailOutboxStore store;
    private final QueuedEmailSender sender;
    private final Duration lease;
    private final MeterRegistry metrics;
    public EmailOutboxWorker(EmailOutboxStore store, QueuedEmailSender sender,
            BrevoEmailProperties properties, MeterRegistry metrics) {
        this.store = store; this.sender = sender; this.metrics = metrics;
        lease = properties.connectTimeout().plus(properties.readTimeout()).plusSeconds(60);
        metrics.gauge("lootfast.email.outbox.pending", store, EmailOutboxStore::pendingCount);
    }
    @Scheduled(fixedDelayString="PT5S", initialDelayString="PT5S", scheduler="emailOutboxScheduler")
    public void sweep() {
        try {
            for (int i=0; i<50; i++) {
                var job = store.claim(lease);
                if (job.isEmpty()) break;
                deliver(job.get());
            }
        } catch (RuntimeException exception) {
            // No exception body: JDBC/transport exceptions may contain email addresses or content.
            log.error("Email outbox sweep failed ({})", exception.getClass().getSimpleName());
            metrics.counter("lootfast.email.outbox.errors").increment();
        }
    }
    public void deliver(ClaimedEmail job) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Email delivery must run outside a database transaction");
        }
        EmailDeliveryResult result;
        try { result = sender.sendQueuedEmail(job.recipient(), job.id(), job.subject(), job.htmlBody()); }
        catch (RuntimeException exception) { result = EmailDeliveryResult.retry("UNEXPECTED_TRANSPORT_ERROR", Duration.ZERO); }
        boolean updated = store.finish(job, result);
        metrics.counter("lootfast.email.outbox.results", "outcome", updated ? result.outcome().name() : "STALE_WORKER").increment();
        log.info("Email outbox job {} result={} recorded={}", job.id(), result.outcome(), updated);
    }
}
