package br.com.holding.payments.report;

import br.com.holding.payments.report.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final ReportRepository reportRepository;

    @Transactional(readOnly = true)
    public List<RevenueReportEntry> getRevenue(LocalDate from, LocalDate to, String groupBy) {
        List<Object[]> rows = switch (groupBy.toLowerCase()) {
            case "method" -> reportRepository.revenueByMethod(from, to);
            case "day" -> reportRepository.revenueByDay(from, to);
            case "origin" -> reportRepository.revenueByOrigin(from, to);
            default -> reportRepository.revenueByOrigin(from, to);
        };

        return rows.stream()
                .map(row -> new RevenueReportEntry(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        (BigDecimal) row[2]))
                .toList();
    }

    @Transactional(readOnly = true)
    public MrrReport getMrr() {
        BigDecimal mrr = reportRepository.calculateMrr();
        long activeCount = reportRepository.countActiveSubscriptions();
        BigDecimal arr = mrr.multiply(BigDecimal.valueOf(12));

        return new MrrReport(LocalDateTime.now(), activeCount, mrr, arr);
    }

    @Transactional(readOnly = true)
    public ChurnReport getChurn(LocalDate from, LocalDate to) {
        long canceled = reportRepository.countCanceledInPeriod(from, to);
        long activeAtStart = reportRepository.countActiveAtDate(from);

        BigDecimal churnRate = activeAtStart > 0
                ? BigDecimal.valueOf(canceled)
                    .divide(BigDecimal.valueOf(activeAtStart), 4, RoundingMode.HALF_EVEN)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_EVEN)
                : BigDecimal.ZERO;

        return new ChurnReport(LocalDateTime.now(), from, to, canceled, activeAtStart, churnRate);
    }

    @Transactional(readOnly = true)
    public List<OverdueReportEntry> getOverdue() {
        return reportRepository.findOverdueSummary().stream()
                .map(row -> new OverdueReportEntry(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        ((Number) row[2]).longValue(),
                        (BigDecimal) row[3]))
                .toList();
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        // MRR / ARR
        BigDecimal mrr = reportRepository.calculateMrr();
        BigDecimal arr = mrr.multiply(BigDecimal.valueOf(12));
        long activeSubscriptions = reportRepository.countActiveSubscriptions();

        // Totais
        long totalCustomers = reportRepository.countActiveCustomers();
        long totalCharges = reportRepository.countAllCharges();
        long totalOverdueCharges = reportRepository.countOverdueCharges();
        BigDecimal totalOverdueValue = reportRepository.sumOverdueValue();

        // Mês atual
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate monthEnd = LocalDate.now();

        BigDecimal revenueCurrentMonth = reportRepository.sumRevenueInPeriod(monthStart, monthEnd);
        long chargesReceivedCurrentMonth = reportRepository.countReceivedChargesInPeriod(monthStart, monthEnd);

        // Churn do mês
        long canceledCurrentMonth = reportRepository.countCanceledInPeriod(monthStart, monthEnd);
        long activeAtMonthStart = reportRepository.countActiveAtDate(monthStart);
        BigDecimal churnRate = activeAtMonthStart > 0
                ? BigDecimal.valueOf(canceledCurrentMonth)
                    .divide(BigDecimal.valueOf(activeAtMonthStart), 4, RoundingMode.HALF_EVEN)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_EVEN)
                : BigDecimal.ZERO;

        // Receita por método (mês atual)
        List<RevenueReportEntry> revenueByMethod = reportRepository.revenueByMethod(monthStart, monthEnd).stream()
                .map(row -> new RevenueReportEntry(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        (BigDecimal) row[2]))
                .toList();

        // Top inadimplentes (limitado a 10)
        List<OverdueReportEntry> topOverdue = reportRepository.findOverdueSummary().stream()
                .limit(10)
                .map(row -> new OverdueReportEntry(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        ((Number) row[2]).longValue(),
                        (BigDecimal) row[3]))
                .toList();

        return new DashboardResponse(
                LocalDateTime.now(),
                mrr, arr,
                totalCustomers, activeSubscriptions, totalCharges,
                totalOverdueCharges, totalOverdueValue,
                revenueCurrentMonth, chargesReceivedCurrentMonth,
                canceledCurrentMonth, churnRate,
                revenueByMethod, topOverdue
        );
    }
}
