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
}
