package br.com.holding.payments.webhooksubscription;

import br.com.holding.payments.company.EncryptionService;
import br.com.holding.payments.tenant.CrossTenant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class WebhookDeliveryJob {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 10;
    private static final int RESPONSE_EXCERPT_LIMIT = 2048;
    // Mesmos degraus dos webhooks Asaas: 5s, 30s, 2min, 10min, 1h x6
    private static final long[] BACKOFF_SECONDS = {5, 30, 120, 600, 3600, 3600, 3600, 3600, 3600, 3600};

    private final WebhookDeliveryAttemptRepository deliveryRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final EncryptionService encryptionService;
    private final RestClient restClient;

    public WebhookDeliveryJob(WebhookDeliveryAttemptRepository deliveryRepository,
                              WebhookSubscriptionRepository subscriptionRepository,
                              EncryptionService encryptionService) {
        this.deliveryRepository = deliveryRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.encryptionService = encryptionService;
        this.restClient = RestClient.builder()
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
                    setReadTimeout((int) Duration.ofSeconds(10).toMillis());
                }})
                .build();
    }

    @Scheduled(fixedDelayString = "${app.webhook-subscriptions.relay-interval-ms:5000}")
    @Transactional
    @CrossTenant(reason = "Entregas de webhook sao processadas em lote cross-tenant; tenant real esta em cada row")
    public void deliverPending() {
        List<WebhookDeliveryAttempt> ready = deliveryRepository.findReadyForDelivery(
                LocalDateTime.now(), BATCH_SIZE);
        if (ready.isEmpty()) return;

        log.debug("Processando {} entregas de webhook", ready.size());
        for (WebhookDeliveryAttempt attempt : ready) {
            deliver(attempt);
        }
    }

    private void deliver(WebhookDeliveryAttempt attempt) {
        WebhookSubscription sub = attempt.getSubscription();

        // Se a subscription foi desativada depois de enfileirar, joga pra DLQ
        if (sub == null || !Boolean.TRUE.equals(sub.getActive())) {
            attempt.markDlq("Subscription inativa ou removida");
            deliveryRepository.save(attempt);
            return;
        }

        String rawToken = encryptionService.decrypt(sub.getTokenEncrypted());

        long start = System.nanoTime();
        Integer responseStatus = null;
        String responseExcerpt = null;
        String errorMessage = null;
        boolean success = false;

        try {
            var response = restClient.post()
                    .uri(attempt.getUrl())
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + rawToken)
                    .header("X-Token-Prefix", sub.getTokenPrefix())
                    .header("X-Event-Id", attempt.getEventId())
                    .header("X-Event-Type", attempt.getEventType())
                    .header("X-Delivery-Id", attempt.getId().toString())
                    .header("X-Delivery-Attempt", attempt.getAttemptNumber().toString())
                    .body(attempt.getRequestBody())
                    .retrieve()
                    .toEntity(String.class);

            HttpStatusCode sc = response.getStatusCode();
            responseStatus = sc.value();
            responseExcerpt = truncate(response.getBody());
            success = sc.is2xxSuccessful();
            if (!success) {
                errorMessage = "HTTP " + sc.value();
            }
        } catch (RestClientResponseException e) {
            responseStatus = e.getStatusCode().value();
            responseExcerpt = truncate(e.getResponseBodyAsString());
            errorMessage = "HTTP " + responseStatus + ": " + e.getStatusText();
        } catch (Exception e) {
            errorMessage = e.getClass().getSimpleName() + ": "
                    + (e.getMessage() != null ? e.getMessage() : "sem mensagem");
        }

        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        sub.setLastDeliveryAt(LocalDateTime.now());

        if (success) {
            attempt.markDelivered(responseStatus, responseExcerpt, durationMs);
            sub.setLastSuccessAt(LocalDateTime.now());
            subscriptionRepository.save(sub);
            deliveryRepository.save(attempt);
            log.info("Webhook entregue: attemptId={}, sub={}, event={}, httpStatus={}, duration={}ms",
                    attempt.getId(), sub.getId(), attempt.getEventType(), responseStatus, durationMs);
            return;
        }

        subscriptionRepository.save(sub);

        if (attempt.getAttemptNumber() >= MAX_ATTEMPTS) {
            attempt.markFailed(responseStatus, responseExcerpt, durationMs,
                    "Max tentativas atingido. Ultimo erro: " + errorMessage, null);
            attempt.markDlq("Max tentativas. " + errorMessage);
            deliveryRepository.save(attempt);
            log.error("Webhook em DLQ: attemptId={}, sub={}, event={}, tentativas={}, ultimoErro={}",
                    attempt.getId(), sub.getId(), attempt.getEventType(),
                    attempt.getAttemptNumber(), errorMessage);
            return;
        }

        long backoff = BACKOFF_SECONDS[Math.min(attempt.getAttemptNumber() - 1, BACKOFF_SECONDS.length - 1)];
        LocalDateTime next = LocalDateTime.now().plusSeconds(backoff);
        attempt.setAttemptNumber(attempt.getAttemptNumber() + 1);
        attempt.markFailed(responseStatus, responseExcerpt, durationMs, errorMessage, next);
        // Volta pra PENDING para o proximo ciclo pegar
        attempt.markPendingRetry(next);
        deliveryRepository.save(attempt);

        log.warn("Webhook falhou, reagendando: attemptId={}, sub={}, event={}, proximaTentativa={}s, erro={}",
                attempt.getId(), sub.getId(), attempt.getEventType(), backoff, errorMessage);
    }

    private String truncate(String body) {
        if (body == null) return null;
        return body.length() <= RESPONSE_EXCERPT_LIMIT
                ? body
                : body.substring(0, RESPONSE_EXCERPT_LIMIT) + "...";
    }
}
