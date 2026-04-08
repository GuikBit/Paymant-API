package br.com.holding.payments.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {

    public OutboxMetrics(OutboxEventRepository outboxEventRepository, MeterRegistry meterRegistry) {
        Gauge.builder("outbox_lag_seconds", outboxEventRepository, repo -> {
                    Double lag = repo.calculateLagSeconds();
                    return lag != null ? lag : 0.0;
                })
                .description("Age in seconds of the oldest pending outbox event")
                .register(meterRegistry);

        Gauge.builder("outbox_pending_count", outboxEventRepository,
                        repo -> repo.countByStatus(OutboxStatus.PENDING))
                .description("Number of pending outbox events")
                .register(meterRegistry);

        Gauge.builder("outbox_dlq_count", outboxEventRepository,
                        repo -> repo.countByStatus(OutboxStatus.DLQ))
                .description("Number of outbox events in DLQ")
                .register(meterRegistry);
    }
}
