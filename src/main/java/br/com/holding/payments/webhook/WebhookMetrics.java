package br.com.holding.payments.webhook;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class WebhookMetrics {

    public WebhookMetrics(WebhookEventRepository webhookEventRepository, MeterRegistry meterRegistry) {
        Gauge.builder("webhook_pending_count", webhookEventRepository,
                        repo -> repo.countByStatus(WebhookEventStatus.PENDING))
                .description("Number of pending webhook events")
                .register(meterRegistry);

        Gauge.builder("webhook_deferred_count", webhookEventRepository,
                        WebhookEventRepository::countDeferred)
                .description("Number of deferred webhook events")
                .register(meterRegistry);

        Gauge.builder("webhook_dlq_count", webhookEventRepository,
                        WebhookEventRepository::countDlq)
                .description("Number of webhook events in DLQ")
                .register(meterRegistry);

        Gauge.builder("webhook_lag_seconds", webhookEventRepository, repo -> {
                    Double lag = repo.calculateLagSeconds();
                    return lag != null ? lag : 0.0;
                })
                .description("Age in seconds of the oldest pending/deferred webhook event")
                .register(meterRegistry);
    }
}
