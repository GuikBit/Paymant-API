package br.com.holding.payments.report.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MrrReport(
        LocalDateTime calculatedAt,
        long activeSubscriptions,
        BigDecimal mrr,
        BigDecimal arr
) {}
