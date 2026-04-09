package br.com.holding.payments.webhook;

public enum WebhookEventStatus {
    PENDING,
    PROCESSING,
    DEFERRED,
    PROCESSED,
    FAILED,
    DLQ
}
