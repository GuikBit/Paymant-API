package br.com.holding.payments.outbox.dto;

public record OutboxSummaryResponse(
        long pending,
        long published,
        long failed,
        long dlq,
        Double lagSeconds
) {}
