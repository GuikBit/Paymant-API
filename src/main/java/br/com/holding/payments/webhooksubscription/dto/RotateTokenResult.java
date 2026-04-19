package br.com.holding.payments.webhooksubscription.dto;

public record RotateTokenResult(
        Long subscriptionId,
        String tokenPrefix,
        String rawToken
) {}
