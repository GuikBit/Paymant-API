package br.com.holding.payments.outbox.dto;

import br.com.holding.payments.outbox.OutboxStatus;

import java.time.LocalDateTime;

public record OutboxEventResponse(
        Long id,
        Long companyId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        OutboxStatus status,
        Integer attemptCount,
        LocalDateTime createdAt,
        LocalDateTime publishedAt,
        String lastError
) {}
