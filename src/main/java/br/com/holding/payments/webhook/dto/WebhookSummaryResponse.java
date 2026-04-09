package br.com.holding.payments.webhook.dto;

public record WebhookSummaryResponse(
        long pending,
        long processing,
        long deferred,
        long processed,
        long failed,
        long dlq,
        long total
) {}
