package br.com.holding.payments.webhooksubscription.dto;

public record WebhookEventCatalogResponse(
        String eventType,
        String resourceType,
        String description
) {}
