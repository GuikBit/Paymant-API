package br.com.holding.payments.reconciliation.dto;

import java.time.LocalDateTime;

public record DlqReplayResult(
        LocalDateTime executedAt,
        long webhookEventsReplayed,
        long outboxEventsReplayed,
        long totalReplayed
) {}
