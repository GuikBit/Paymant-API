package br.com.holding.payments.webhooksubscription.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateWebhookSubscriptionRequest(
        @Size(max = 100) String name,
        @Size(max = 500) String description,
        @Size(max = 2000) String url,
        List<String> eventTypes,
        Boolean active
) {}
