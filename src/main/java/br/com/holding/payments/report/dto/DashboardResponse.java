package br.com.holding.payments.report.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DashboardResponse(
        LocalDateTime calculatedAt,

        // Receita recorrente
        BigDecimal mrr,
        BigDecimal arr,

        // Totais
        long totalCustomers,
        long totalActiveSubscriptions,
        long totalCharges,
        long totalOverdueCharges,
        BigDecimal totalOverdueValue,

        // Receita do mês atual
        BigDecimal revenueCurrentMonth,
        long chargesReceivedCurrentMonth,

        // Churn do mês atual
        long canceledCurrentMonth,
        BigDecimal churnRateCurrentMonth,

        // Receita por método de pagamento (mês atual)
        List<RevenueReportEntry> revenueByMethod,

        // Top clientes inadimplentes
        List<OverdueReportEntry> topOverdueCustomers
) {}
