package br.com.holding.payments.webhook.dto;

import br.com.holding.payments.webhook.WebhookEventStatus;

import java.time.LocalDateTime;

public record WebhookEventResponse(
        Long id,
        Long companyId,
        String asaasEventId,
        String eventType,
        WebhookEventStatus status,
        Integer attemptCount,
        LocalDateTime nextAttemptAt,
        LocalDateTime processedAt,
        String lastError,
        LocalDateTime receivedAt
) {}
