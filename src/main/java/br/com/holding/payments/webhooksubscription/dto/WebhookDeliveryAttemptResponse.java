package br.com.holding.payments.webhooksubscription.dto;

import br.com.holding.payments.webhooksubscription.WebhookDeliveryStatus;

import java.time.LocalDateTime;

public record WebhookDeliveryAttemptResponse(
        Long id,
        Long subscriptionId,
        String eventType,
        String eventId,
        String url,
        Integer responseStatus,
        String responseBodyExcerpt,
        Long durationMs,
        Integer attemptNumber,
        WebhookDeliveryStatus status,
        String errorMessage,
        LocalDateTime nextAttemptAt,
        LocalDateTime createdAt,
        LocalDateTime deliveredAt
) {}
