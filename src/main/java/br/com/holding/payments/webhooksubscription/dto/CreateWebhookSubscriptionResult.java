package br.com.holding.payments.webhooksubscription.dto;

public record CreateWebhookSubscriptionResult(
        WebhookSubscriptionResponse subscription,
        String rawToken
) {}
