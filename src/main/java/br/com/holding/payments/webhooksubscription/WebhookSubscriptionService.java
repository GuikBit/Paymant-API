package br.com.holding.payments.webhooksubscription;

import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.common.errors.ResourceNotFoundException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.company.EncryptionService;
import br.com.holding.payments.tenant.TenantContext;
import br.com.holding.payments.webhooksubscription.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookSubscriptionService {

    private static final String TOKEN_PREFIX = "wht_";
    private static final int TOKEN_BYTES = 36;
    private static final int MAX_ACTIVE_PER_COMPANY = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final WebhookSubscriptionRepository repository;
    private final CompanyRepository companyRepository;
    private final WebhookDispatcher dispatcher;
    private final ObjectMapper objectMapper;
    private final EncryptionService encryptionService;

    @Transactional
    public CreateWebhookSubscriptionResult create(CreateWebhookSubscriptionRequest request) {
        Long companyId = TenantContext.getRequiredCompanyId();

        validateUrl(request.url());
        validateEventTypes(request.eventTypes());

        long active = repository.countByCompanyIdAndActiveTrue(companyId);
        if (active >= MAX_ACTIVE_PER_COMPANY) {
            throw new BusinessException(
                    "Limite de " + MAX_ACTIVE_PER_COMPANY + " webhooks ativos por empresa atingido.");
        }

        Company company = companyRepository.getReferenceById(companyId);

        String rawToken = generateRawToken();
        String tokenEncrypted = encryptionService.encrypt(rawToken);
        String tokenPrefix = rawToken.substring(0, Math.min(12, rawToken.length()));

        WebhookSubscription sub = WebhookSubscription.builder()
                .company(company)
                .name(request.name())
                .description(request.description())
                .url(request.url())
                .tokenEncrypted(tokenEncrypted)
                .tokenPrefix(tokenPrefix)
                .eventTypes(request.eventTypes().toArray(new String[0]))
                .active(true)
                .build();

        sub = repository.save(sub);
        log.info("Webhook subscription created: id={}, company={}, url={}", sub.getId(), companyId, request.url());

        return new CreateWebhookSubscriptionResult(toResponse(sub), rawToken);
    }

    @Transactional(readOnly = true)
    public Page<WebhookSubscriptionResponse> list(Pageable pageable) {
        Long companyId = TenantContext.getRequiredCompanyId();
        return repository.findByCompanyId(companyId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public WebhookSubscriptionResponse findById(Long id) {
        return toResponse(loadOwned(id));
    }

    @Transactional
    public WebhookSubscriptionResponse update(Long id, UpdateWebhookSubscriptionRequest request) {
        WebhookSubscription sub = loadOwned(id);

        if (request.url() != null && !request.url().isBlank()) {
            validateUrl(request.url());
            sub.setUrl(request.url());
        }
        if (request.name() != null && !request.name().isBlank()) {
            sub.setName(request.name());
        }
        if (request.description() != null) {
            sub.setDescription(request.description());
        }
        if (request.eventTypes() != null && !request.eventTypes().isEmpty()) {
            validateEventTypes(request.eventTypes());
            sub.setEventTypes(request.eventTypes().toArray(new String[0]));
        }
        if (request.active() != null) {
            sub.setActive(request.active());
        }

        repository.save(sub);
        return toResponse(sub);
    }

    @Transactional
    public void delete(Long id) {
        WebhookSubscription sub = loadOwned(id);
        repository.delete(sub);
        log.info("Webhook subscription deleted: id={}", id);
    }

    @Transactional
    public RotateTokenResult rotateToken(Long id) {
        WebhookSubscription sub = loadOwned(id);

        String rawToken = generateRawToken();
        sub.setTokenEncrypted(encryptionService.encrypt(rawToken));
        sub.setTokenPrefix(rawToken.substring(0, Math.min(12, rawToken.length())));

        repository.save(sub);
        log.info("Webhook subscription token rotated: id={}", id);

        return new RotateTokenResult(sub.getId(), sub.getTokenPrefix(), rawToken);
    }

    @Transactional
    public WebhookDeliveryAttemptResponse ping(Long id) {
        WebhookSubscription sub = loadOwned(id);

        ObjectNode data = objectMapper.createObjectNode();
        data.put("message", "Este e um evento de teste enviado pelo botao de ping.");
        data.put("subscriptionId", sub.getId());
        data.put("timestamp", LocalDateTime.now().toString());

        WebhookDeliveryAttempt attempt = dispatcher.enqueueForSubscription(
                sub,
                WebhookEventCatalog.WebhookTestEvent.name(),
                "test_" + System.currentTimeMillis(),
                "WebhookSubscription",
                sub.getId().toString(),
                data,
                null);

        return toAttemptResponse(attempt);
    }

    @Transactional(readOnly = true)
    public Page<WebhookDeliveryAttemptResponse> listDeliveries(Long subscriptionId, Pageable pageable) {
        loadOwned(subscriptionId); // validates ownership
        return dispatcher.listDeliveries(subscriptionId, pageable).map(this::toAttemptResponse);
    }

    public List<WebhookEventCatalogResponse> listCatalog() {
        return WebhookEventCatalog.all().stream()
                .map(e -> new WebhookEventCatalogResponse(e.name(), e.resourceType(), e.description()))
                .toList();
    }

    // ==================== PRIVATE ====================

    private WebhookSubscription loadOwned(Long id) {
        Long companyId = TenantContext.getRequiredCompanyId();
        return repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookSubscription", id));
    }

    private void validateUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("URL invalida: " + url);
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new BusinessException("URL precisa usar http ou https.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BusinessException("URL precisa ter um host valido.");
        }
    }

    private void validateEventTypes(List<String> eventTypes) {
        for (String t : eventTypes) {
            if (!WebhookEventCatalog.isPublic(t)) {
                throw new BusinessException("Evento nao suportado: " + t
                        + ". Consulte GET /api/v1/webhook-subscriptions/event-types para a lista.");
            }
        }
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    WebhookSubscriptionResponse toResponse(WebhookSubscription sub) {
        return new WebhookSubscriptionResponse(
                sub.getId(),
                sub.getCompany().getId(),
                sub.getName(),
                sub.getDescription(),
                sub.getUrl(),
                sub.getTokenPrefix(),
                sub.getEventTypes() != null ? List.of(sub.getEventTypes()) : List.of(),
                sub.getActive(),
                sub.getLastDeliveryAt(),
                sub.getLastSuccessAt(),
                sub.getCreatedAt(),
                sub.getUpdatedAt()
        );
    }

    WebhookDeliveryAttemptResponse toAttemptResponse(WebhookDeliveryAttempt a) {
        return new WebhookDeliveryAttemptResponse(
                a.getId(),
                a.getSubscription().getId(),
                a.getEventType(),
                a.getEventId(),
                a.getUrl(),
                a.getResponseStatus(),
                a.getResponseBodyExcerpt(),
                a.getDurationMs(),
                a.getAttemptNumber(),
                a.getStatus(),
                a.getErrorMessage(),
                a.getNextAttemptAt(),
                a.getCreatedAt(),
                a.getDeliveredAt()
        );
    }
}
