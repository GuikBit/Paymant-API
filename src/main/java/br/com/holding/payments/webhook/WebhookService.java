package br.com.holding.payments.webhook;

import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.common.errors.ResourceNotFoundException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.webhook.dto.WebhookEventResponse;
import br.com.holding.payments.webhook.dto.WebhookSummaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final CompanyRepository companyRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.webhook.max-attempts:10}")
    private int maxAttempts;

    /**
     * Receives a raw webhook payload from Asaas, validates the token,
     * persists the event, and returns immediately.
     */
    @Transactional
    public void receive(Long companyId, String accessToken, String rawPayload) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        validateToken(company, accessToken);

        String asaasEventId = extractEventId(rawPayload);
        String eventType = extractEventType(rawPayload);

        try {
            WebhookEvent event = WebhookEvent.builder()
                    .company(company)
                    .asaasEventId(asaasEventId)
                    .eventType(eventType)
                    .payload(rawPayload)
                    .build();

            webhookEventRepository.save(event);
            log.info("Webhook event received: asaasEventId={}, type={}, company={}",
                    asaasEventId, eventType, companyId);
        } catch (DataIntegrityViolationException e) {
            // Duplicate event - constraint uq_webhook_event catches it
            log.info("Duplicate webhook event ignored: asaasEventId={}, company={}",
                    asaasEventId, companyId);
        }
    }

    @Transactional(readOnly = true)
    public Page<WebhookEventResponse> findByStatus(WebhookEventStatus status, Pageable pageable) {
        return webhookEventRepository.findByStatus(status, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<WebhookEventResponse> findAll(WebhookEventStatus status, String eventType, Pageable pageable) {
        Page<WebhookEvent> page;
        if (status != null && eventType != null && !eventType.isBlank()) {
            page = webhookEventRepository.findByStatusAndEventType(status, eventType, pageable);
        } else if (status != null) {
            page = webhookEventRepository.findByStatus(status, pageable);
        } else if (eventType != null && !eventType.isBlank()) {
            page = webhookEventRepository.findByEventType(eventType, pageable);
        } else {
            page = webhookEventRepository.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public WebhookEventResponse findOne(Long id) {
        WebhookEvent event = webhookEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookEvent", id));
        return toResponse(event);
    }

    @Transactional(readOnly = true)
    public String getPayload(Long id) {
        return webhookEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookEvent", id))
                .getPayload();
    }

    @Transactional(readOnly = true)
    public WebhookSummaryResponse getSummary() {
        long pending = webhookEventRepository.countByStatus(WebhookEventStatus.PENDING);
        long processing = webhookEventRepository.countByStatus(WebhookEventStatus.PROCESSING);
        long deferred = webhookEventRepository.countByStatus(WebhookEventStatus.DEFERRED);
        long processed = webhookEventRepository.countByStatus(WebhookEventStatus.PROCESSED);
        long failed = webhookEventRepository.countByStatus(WebhookEventStatus.FAILED);
        long dlq = webhookEventRepository.countByStatus(WebhookEventStatus.DLQ);
        long total = pending + processing + deferred + processed + failed + dlq;

        return new WebhookSummaryResponse(pending, processing, deferred, processed, failed, dlq, total);
    }

    @Transactional
    public WebhookEventResponse cancel(Long eventId, String reason) {
        WebhookEvent event = webhookEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookEvent", eventId));

        if (event.getStatus() != WebhookEventStatus.DEFERRED &&
                event.getStatus() != WebhookEventStatus.PENDING &&
                event.getStatus() != WebhookEventStatus.FAILED) {
            throw new BusinessException(
                    "Apenas eventos com status DEFERRED, PENDING ou FAILED podem ser cancelados. Status atual: "
                            + event.getStatus());
        }

        String cancellationReason = reason != null && !reason.isBlank()
                ? reason
                : "Cancelado manualmente";
        event.markDlq(cancellationReason);
        webhookEventRepository.save(event);

        log.info("Webhook event cancelled: id={}, asaasEventId={}, reason={}",
                eventId, event.getAsaasEventId(), cancellationReason);
        return toResponse(event);
    }

    @Transactional
    public WebhookEventResponse replay(Long eventId) {
        WebhookEvent event = webhookEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookEvent", eventId));

        if (event.getStatus() != WebhookEventStatus.FAILED &&
                event.getStatus() != WebhookEventStatus.DLQ) {
            throw new BusinessException(
                    "Apenas eventos com status FAILED ou DLQ podem ser reprocessados. Status atual: " + event.getStatus());
        }

        event.markReadyForRetry();
        webhookEventRepository.save(event);

        log.info("Webhook event marked for replay: id={}, asaasEventId={}", eventId, event.getAsaasEventId());
        return toResponse(event);
    }

    /**
     * Called when a charge is created to accelerate processing of deferred events.
     */
    @Transactional
    public void accelerateDeferredForAsaasId(String asaasId) {
        int updated = webhookEventRepository.markDeferredReadyByAsaasId(asaasId);
        if (updated > 0) {
            log.info("Accelerated {} deferred webhook events for asaasId={}", updated, asaasId);
        }
    }

    private void validateToken(Company company, String accessToken) {
        if (company.getWebhookToken() == null || company.getWebhookToken().isBlank()) {
            log.warn("Company {} has no webhook token configured, skipping validation", company.getId());
            return;
        }
        if (!company.getWebhookToken().equals(accessToken)) {
            throw new BusinessException("Token de webhook invalido");
        }
    }

    private String extractEventId(String rawPayload) {
        try {
            var node = objectMapper.readTree(rawPayload);
            var idNode = node.get("id");
            if (idNode != null && !idNode.isNull()) {
                return idNode.asText();
            }
            // Fallback: use event + payment.id as unique identifier
            String event = node.has("event") ? node.get("event").asText() : "UNKNOWN";
            String paymentId = "";
            if (node.has("payment") && node.get("payment").has("id")) {
                paymentId = node.get("payment").get("id").asText();
            }
            return event + "_" + paymentId + "_" + System.currentTimeMillis();
        } catch (Exception e) {
            log.error("Failed to extract event ID from webhook payload", e);
            return "UNKNOWN_" + System.currentTimeMillis();
        }
    }

    private String extractEventType(String rawPayload) {
        try {
            var node = objectMapper.readTree(rawPayload);
            if (node.has("event")) {
                return node.get("event").asText();
            }
            return "UNKNOWN";
        } catch (Exception e) {
            log.error("Failed to extract event type from webhook payload", e);
            return "UNKNOWN";
        }
    }

    WebhookEventResponse toResponse(WebhookEvent event) {
        return new WebhookEventResponse(
                event.getId(),
                event.getCompany().getId(),
                event.getAsaasEventId(),
                event.getEventType(),
                event.getStatus(),
                event.getAttemptCount(),
                event.getNextAttemptAt(),
                event.getProcessedAt(),
                event.getReceivedAt(),
                event.getLastError(),
                event.getProcessedResourceType(),
                event.getProcessedResourceId(),
                event.getProcessedAsaasId(),
                event.getProcessingSummary(),
                event.getProcessingDurationMs()
        );
    }
}
