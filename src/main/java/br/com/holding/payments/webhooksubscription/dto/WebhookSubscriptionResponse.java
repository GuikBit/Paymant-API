package br.com.holding.payments.webhooksubscription.dto;

import java.time.LocalDateTime;
import java.util.List;

public record WebhookSubscriptionResponse(
        Long id,
        Long companyId,
        String name,
        String description,
        String url,
        String tokenPrefix,
        List<String> eventTypes,
        Boolean active,
        LocalDateTime lastDeliveryAt,
        LocalDateTime lastSuccessAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
