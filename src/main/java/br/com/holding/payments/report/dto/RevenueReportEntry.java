package br.com.holding.payments.report.dto;

import java.math.BigDecimal;

public record RevenueReportEntry(
        String groupKey,
        long chargeCount,
        BigDecimal totalValue
) {}
