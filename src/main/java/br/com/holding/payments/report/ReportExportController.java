package br.com.holding.payments.report;

import br.com.holding.payments.report.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports/export")
@RequiredArgsConstructor
@Tag(name = "Exportacao de Relatorios", description = "Download de relatorios em CSV")
public class ReportExportController {

    private final ReportService reportService;

    @GetMapping("/revenue")
    @Operation(summary = "Exportar faturamento em CSV")
    public void exportRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "origin") String groupBy,
            HttpServletResponse response) throws IOException {

        List<RevenueReportEntry> data = reportService.getRevenue(from, to, groupBy);

        setCsvHeaders(response, "revenue_" + from + "_" + to + ".csv");
        PrintWriter writer = response.getWriter();
        writer.println("Agrupamento,Quantidade,Valor Total");
        for (RevenueReportEntry entry : data) {
            writer.printf("%s,%d,%s%n", entry.groupKey(), entry.chargeCount(), entry.totalValue());
        }
        writer.flush();
    }

    @GetMapping("/mrr")
    @Operation(summary = "Exportar MRR/ARR em CSV")
    public void exportMrr(HttpServletResponse response) throws IOException {

        MrrReport data = reportService.getMrr();

        setCsvHeaders(response, "mrr_report.csv");
        PrintWriter writer = response.getWriter();
        writer.println("Calculado Em,Assinaturas Ativas,MRR,ARR");
        writer.printf("%s,%d,%s,%s%n", data.calculatedAt(), data.activeSubscriptions(), data.mrr(), data.arr());
        writer.flush();
    }

    @GetMapping("/churn")
    @Operation(summary = "Exportar churn em CSV")
    public void exportChurn(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletResponse response) throws IOException {

        ChurnReport data = reportService.getChurn(from, to);

        setCsvHeaders(response, "churn_" + from + "_" + to + ".csv");
        PrintWriter writer = response.getWriter();
        writer.println("Periodo Inicio,Periodo Fim,Cancelamentos,Ativas no Inicio,Taxa Churn (%)");
        writer.printf("%s,%s,%d,%d,%s%n",
                data.periodStart(), data.periodEnd(), data.canceledInPeriod(),
                data.activeAtStart(), data.churnRate());
        writer.flush();
    }

    @GetMapping("/overdue")
    @Operation(summary = "Exportar inadimplencia em CSV")
    public void exportOverdue(HttpServletResponse response) throws IOException {

        List<OverdueReportEntry> data = reportService.getOverdue();

        setCsvHeaders(response, "overdue_report.csv");
        PrintWriter writer = response.getWriter();
        writer.println("ID Cliente,Nome Cliente,Qtd Cobranças Atrasadas,Valor Total Atrasado");
        for (OverdueReportEntry entry : data) {
            writer.printf("%d,\"%s\",%d,%s%n",
                    entry.customerId(), entry.customerName(), entry.overdueCount(), entry.totalOverdueValue());
        }
        writer.flush();
    }

    private void setCsvHeaders(HttpServletResponse response, String filename) {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setCharacterEncoding("UTF-8");
    }
}
