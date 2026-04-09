package br.com.holding.payments.report;

import br.com.holding.payments.report.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Relatorios", description = "Revenue, MRR, churn e inadimplencia")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/revenue")
    @Operation(summary = "Relatorio de faturamento",
            description = "Faturamento agrupado por method, day ou origin")
    public List<RevenueReportEntry> revenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "origin") String groupBy) {
        return reportService.getRevenue(from, to, groupBy);
    }

    @GetMapping("/subscriptions/mrr")
    @Operation(summary = "MRR e ARR",
            description = "Monthly Recurring Revenue baseado em assinaturas ativas")
    public MrrReport mrr() {
        return reportService.getMrr();
    }

    @GetMapping("/subscriptions/churn")
    @Operation(summary = "Taxa de churn",
            description = "Percentual de cancelamentos no periodo")
    public ChurnReport churn(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.getChurn(from, to);
    }

    @GetMapping("/overdue")
    @Operation(summary = "Relatorio de inadimplencia",
            description = "Clientes com cobrancas em atraso, agrupados por valor total")
    public List<OverdueReportEntry> overdue() {
        return reportService.getOverdue();
    }
}
