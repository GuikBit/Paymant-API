package br.com.holding.payments.outbox;

import br.com.holding.payments.tenant.CrossTenant;
import br.com.holding.payments.webhooksubscription.WebhookDispatcher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@Slf4j
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final WebhookDispatcher webhookDispatcher;
    private final RestClient restClient;
    private final int maxAttempts;
    private final Counter failedCounter;
    private final String webhookUrl;

    public OutboxRelay(OutboxEventRepository outboxEventRepository,
                       WebhookDispatcher webhookDispatcher,
                       @Value("${app.outbox.max-attempts:5}") int maxAttempts,
                       @Value("${app.outbox.webhook-url:}") String webhookUrl,
                       MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.webhookDispatcher = webhookDispatcher;
        this.maxAttempts = maxAttempts;
        this.webhookUrl = webhookUrl;
        this.restClient = RestClient.builder().build();
        this.failedCounter = Counter.builder("outbox_failed_total")
                .description("Total outbox events that failed to publish")
                .register(meterRegistry);
    }

    @PostConstruct
    void logConfiguration() {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("OutboxRelay: nenhum webhook-url configurado (app.outbox.webhook-url). "
                    + "Eventos serao marcados como PUBLISHED sem envio real. "
                    + "Configure a propriedade para ambientes de staging/producao.");
        } else {
            log.info("OutboxRelay: configurado para enviar eventos para {}", webhookUrl);
        }
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay-interval-ms:5000}")
    @Transactional
    @CrossTenant(reason = "Outbox relay processes events from all tenants")
    public void relayPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findPendingEventsForProcessing(50);

        if (events.isEmpty()) {
            return;
        }

        log.debug("Relaying {} outbox events", events.size());

        for (OutboxEvent event : events) {
            try {
                publishEvent(event);
                event.markPublished();
                log.debug("Published outbox event: id={}, type={}", event.getId(), event.getEventType());
            } catch (Exception e) {
                handleFailure(event, e);
            }

            // Fan out para as webhook subscriptions do tenant (independente do global).
            // Rodadas separadas: se o global falhou, as subscriptions ainda recebem;
            // se elas falharem, cada uma tem seu proprio retry.
            try {
                webhookDispatcher.dispatch(event);
            } catch (Exception dispatchError) {
                log.error("Falha ao enfileirar webhook dispatch para event id={}: {}",
                        event.getId(), dispatchError.getMessage(), dispatchError);
            }
        }
    }

    private void publishEvent(OutboxEvent event) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("No webhook URL configured, marking event as published: id={}", event.getId());
            return;
        }

        restClient.post()
                .uri(webhookUrl)
                .header("Content-Type", "application/json")
                .header("X-Event-Type", event.getEventType())
                .header("X-Aggregate-Type", event.getAggregateType())
                .header("X-Aggregate-Id", event.getAggregateId())
                .header("X-Event-Id", event.getId().toString())
                .body(event.getPayload())
                .retrieve()
                .toBodilessEntity();
    }

    private void handleFailure(OutboxEvent event, Exception e) {
        String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        event.markFailed(error);
        failedCounter.increment();

        if (event.getAttemptCount() >= maxAttempts) {
            event.markDlq(error);
            log.error("Outbox event moved to DLQ after {} attempts: id={}, type={}, error={}",
                    maxAttempts, event.getId(), event.getEventType(), error);
        } else {
            log.warn("Outbox event publish failed (attempt {}/{}): id={}, type={}, error={}",
                    event.getAttemptCount(), maxAttempts, event.getId(), event.getEventType(), error);
        }
    }
}
