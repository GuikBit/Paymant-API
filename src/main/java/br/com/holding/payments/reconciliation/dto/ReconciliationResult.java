package br.com.holding.payments.reconciliation.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ReconciliationResult(
        LocalDateTime executedAt,
        int totalChecked,
        int divergencesFound,
        int autoFixed,
        List<Divergence> divergences
) {
    public record Divergence(
            String entityType,
            Long localId,
            String asaasId,
            String localStatus,
            String asaasStatus,
            String action
    ) {}
}
