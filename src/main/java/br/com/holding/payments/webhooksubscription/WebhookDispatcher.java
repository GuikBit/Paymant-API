package br.com.holding.payments.webhooksubscription;

import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.outbox.OutboxEvent;
import br.com.holding.payments.webhooksubscription.dto.WebhookEnvelope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookDispatcher {

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryAttemptRepository deliveryRepository;
    private final CompanyRepository companyRepository;
    private final ObjectMapper objectMapper;

    /**
     * Fan out: cria uma WebhookDeliveryAttempt PENDING para cada subscription
     * ativa da company que assina o eventType do OutboxEvent.
     * So e chamada para eventos do catalogo publico.
     */
    @Transactional
    public void dispatch(OutboxEvent outboxEvent) {
        if (!WebhookEventCatalog.isPublic(outboxEvent.getEventType())) {
            return;
        }

        Long companyId = outboxEvent.getCompany().getId();
        List<WebhookSubscription> subs = subscriptionRepository.findActiveMatchingEvent(
                companyId, outboxEvent.getEventType());

        if (subs.isEmpty()) return;

        JsonNode data = parsePayload(outboxEvent.getPayload());

        for (WebhookSubscription sub : subs) {
            enqueueForSubscription(
                    sub,
                    outboxEvent.getEventType(),
                    "evt_" + outboxEvent.getId(),
                    outboxEvent.getAggregateType(),
                    outboxEvent.getAggregateId(),
                    data,
                    outboxEvent.getId());
        }
    }

    @Transactional
    public WebhookDeliveryAttempt enqueueForSubscription(WebhookSubscription sub,
                                                        String eventType,
                                                        String eventId,
                                                        String resourceType,
                                                        String resourceId,
                                                        JsonNode data,
                                                        Long outboxEventId) {
        WebhookEnvelope envelope = new WebhookEnvelope(
                eventId,
                eventType,
                LocalDateTime.now(),
                sub.getCompany().getId(),
                new WebhookEnvelope.ResourceRef(resourceType, resourceId),
                data
        );

        String body;
        try {
            body = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            log.error("Falha ao serializar envelope para subscription {}: {}", sub.getId(), e.getMessage());
            throw new RuntimeException("Falha ao serializar envelope", e);
        }

        Company company = companyRepository.getReferenceById(sub.getCompany().getId());

        WebhookDeliveryAttempt attempt = WebhookDeliveryAttempt.builder()
                .company(company)
                .subscription(sub)
                .outboxEventId(outboxEventId)
                .eventType(eventType)
                .eventId(eventId)
                .url(sub.getUrl())
                .requestBody(body)
                .attemptNumber(1)
                .status(WebhookDeliveryStatus.PENDING)
                .nextAttemptAt(LocalDateTime.now())
                .build();

        return deliveryRepository.save(attempt);
    }

    @Transactional(readOnly = true)
    public Page<WebhookDeliveryAttempt> listDeliveries(Long subscriptionId, Pageable pageable) {
        return deliveryRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId, pageable);
    }

    private JsonNode parsePayload(String payloadJson) {
        try {
            return objectMapper.readTree(payloadJson);
        } catch (Exception e) {
            log.warn("Falha ao parsear payload do outbox como JSON, usando NullNode: {}", e.getMessage());
            return objectMapper.nullNode();
        }
    }
}
