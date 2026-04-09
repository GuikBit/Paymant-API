package br.com.holding.payments.report.dto;

import java.math.BigDecimal;

public record OverdueReportEntry(
        Long customerId,
        String customerName,
        long overdueCount,
        BigDecimal totalOverdueValue
) {}
