package br.com.holding.payments.outbox;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED,
    DLQ
}
