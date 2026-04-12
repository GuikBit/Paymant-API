package br.com.holding.payments.webhook;

import br.com.holding.payments.common.errors.IllegalStateTransitionException;
import br.com.holding.payments.tenant.CrossTenant;
import br.com.holding.payments.tenant.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class WebhookProcessor {

    private final WebhookEventRepository webhookEventRepository;
    private final WebhookEventHandler webhookEventHandler;
    private final MeterRegistry meterRegistry;
    private final Timer processingTimer;

    @Value("${app.webhook.max-attempts:10}")
    private int maxAttempts;

    @Value("${app.webhook.backoff-multiplier:2}")
    private int backoffMultiplier;

    private static final int BATCH_SIZE = 50;

    // Backoff schedule: 5s, 30s, 2min, 10min, 1h, 1h, 1h, 1h, 1h, 1h
    private static final long[] BACKOFF_SECONDS = {5, 30, 120, 600, 3600, 3600, 3600, 3600, 3600, 3600};

    public WebhookProcessor(WebhookEventRepository webhookEventRepository,
                            WebhookEventHandler webhookEventHandler,
                            MeterRegistry meterRegistry) {
        this.webhookEventRepository = webhookEventRepository;
        this.webhookEventHandler = webhookEventHandler;
        this.meterRegistry = meterRegistry;
        this.processingTimer = Timer.builder("webhook_processing_duration_seconds")
                .description("Time to process a single webhook event")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${app.webhook.processor-interval-ms:3000}")
    @Transactional
    @CrossTenant(reason = "Processador de webhooks precisa ler eventos de todas as empresas; o tenant e definido por evento em processEvent()")
    public void processEvents() {
        List<WebhookEvent> events = webhookEventRepository.findEventsToProcess(
                LocalDateTime.now(), BATCH_SIZE);

        if (events.isEmpty()) {
            return;
        }

        log.debug("Processing {} webhook events", events.size());

        for (WebhookEvent event : events) {
            processEvent(event);
        }
    }

    private void processEvent(WebhookEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            TenantContext.setCompanyId(event.getCompany().getId());
            event.markProcessing();

            boolean processed = webhookEventHandler.handle(event);

            if (processed) {
                event.markProcessed();
                log.info("Webhook event processed: id={}, type={}", event.getId(), event.getEventType());
            } else {
                // Resource not found - defer with backoff
                handleDeferred(event);
            }

            webhookEventRepository.save(event);

        } catch (IllegalStateTransitionException e) {
            event.markFailed("Invalid state transition: " + e.getMessage());
            webhookEventRepository.save(event);
            log.warn("Webhook event failed (invalid transition): id={}, error={}",
                    event.getId(), e.getMessage());

        } catch (Exception e) {
            handleProcessingError(event, e);

        } finally {
            sample.stop(processingTimer);
            TenantContext.clear();
        }
    }

    private void handleDeferred(WebhookEvent event) {
        int attemptCount = event.getAttemptCount();

        if (attemptCount >= maxAttempts) {
            event.markDlq("Max attempts reached after " + attemptCount + " deferrals. Resource never found.");
            meterRegistry.counter("webhook_dlq_total").increment();
            log.error("Webhook event moved to DLQ: id={}, asaasEventId={}, attempts={}",
                    event.getId(), event.getAsaasEventId(), attemptCount);
            return;
        }

        long backoff = calculateBackoff(attemptCount);
        event.markDeferred("Resource not found yet, deferred", backoff);
        meterRegistry.counter("webhook_deferred_total").increment();

        log.info("Webhook event deferred: id={}, attempt={}, nextRetryIn={}s",
                event.getId(), attemptCount + 1, backoff);
    }

    private void handleProcessingError(WebhookEvent event, Exception e) {
        int attemptCount = event.getAttemptCount() + 1;

        if (attemptCount >= maxAttempts) {
            event.markDlq("Max attempts reached. Last error: " + e.getMessage());
            meterRegistry.counter("webhook_dlq_total").increment();
            log.error("Webhook event moved to DLQ after error: id={}, error={}",
                    event.getId(), e.getMessage(), e);
        } else {
            long backoff = calculateBackoff(attemptCount - 1);
            event.markDeferred("Processing error: " + e.getMessage(), backoff);
            meterRegistry.counter("webhook_deferred_total").increment();
            log.warn("Webhook event processing error, will retry: id={}, attempt={}, error={}",
                    event.getId(), attemptCount, e.getMessage());
        }

        webhookEventRepository.save(event);
    }

    private long calculateBackoff(int attempt) {
        if (attempt < BACKOFF_SECONDS.length) {
            return BACKOFF_SECONDS[attempt];
        }
        return BACKOFF_SECONDS[BACKOFF_SECONDS.length - 1];
    }
}
