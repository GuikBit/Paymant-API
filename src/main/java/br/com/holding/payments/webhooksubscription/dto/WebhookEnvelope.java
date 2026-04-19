package br.com.holding.payments.webhooksubscription.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record WebhookEnvelope(
        String id,
        String type,
        LocalDateTime occurredAt,
        Long companyId,
        ResourceRef resource,
        JsonNode data
) {
    public record ResourceRef(String type, String id) {}
}
