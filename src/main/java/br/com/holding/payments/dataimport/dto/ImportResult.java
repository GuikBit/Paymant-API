package br.com.holding.payments.dataimport.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ImportResult(
        int customersImported,
        int customersSkipped,
        int subscriptionsImported,
        int subscriptionsSkipped,
        int chargesImported,
        int chargesSkipped,
        int installmentsImported,
        int installmentsSkipped,
        List<String> errors,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {}
