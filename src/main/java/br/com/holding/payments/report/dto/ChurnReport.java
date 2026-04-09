package br.com.holding.payments.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ChurnReport(
        LocalDateTime calculatedAt,
        LocalDate periodStart,
        LocalDate periodEnd,
        long canceledInPeriod,
        long activeAtStart,
        BigDecimal churnRate
) {}
