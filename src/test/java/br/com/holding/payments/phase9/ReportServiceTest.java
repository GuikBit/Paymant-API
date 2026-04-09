package br.com.holding.payments.phase9;

import br.com.holding.payments.report.ReportRepository;
import br.com.holding.payments.report.ReportService;
import br.com.holding.payments.report.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Fase 9 - ReportService")
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private ReportService service;

    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    // ==================== REVENUE ====================

    @Nested
    @DisplayName("getRevenue()")
    class RevenueTests {

        @Test
        @DisplayName("Revenue por method - agrupa por billing_type")
        void revenueByMethod_shouldCallCorrectQuery() {
            Object[] row1 = new Object[]{"PIX", 10L, new BigDecimal("1500.00")};
            Object[] row2 = new Object[]{"CREDIT_CARD", 5L, new BigDecimal("2000.00")};
            when(reportRepository.revenueByMethod(FROM, TO)).thenReturn(List.<Object[]>of(row1, row2));

            List<RevenueReportEntry> result = service.getRevenue(FROM, TO, "method");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).groupKey()).isEqualTo("PIX");
            assertThat(result.get(0).chargeCount()).isEqualTo(10);
            assertThat(result.get(0).totalValue()).isEqualByComparingTo(new BigDecimal("1500.00"));
            assertThat(result.get(1).groupKey()).isEqualTo("CREDIT_CARD");
            verify(reportRepository).revenueByMethod(FROM, TO);
        }

        @Test
        @DisplayName("Revenue por day - agrupa por data")
        void revenueByDay_shouldCallDayQuery() {
            Object[] row = new Object[]{"2026-03-15", 3L, new BigDecimal("500.00")};
            when(reportRepository.revenueByDay(FROM, TO)).thenReturn(List.<Object[]>of(row));

            List<RevenueReportEntry> result = service.getRevenue(FROM, TO, "day");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).groupKey()).isEqualTo("2026-03-15");
            verify(reportRepository).revenueByDay(FROM, TO);
        }

        @Test
        @DisplayName("Revenue por origin - agrupa por origin")
        void revenueByOrigin_shouldCallOriginQuery() {
            Object[] row = new Object[]{"API", 20L, new BigDecimal("5000.00")};
            when(reportRepository.revenueByOrigin(FROM, TO)).thenReturn(List.<Object[]>of(row));

            List<RevenueReportEntry> result = service.getRevenue(FROM, TO, "origin");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).groupKey()).isEqualTo("API");
            verify(reportRepository).revenueByOrigin(FROM, TO);
        }

        @Test
        @DisplayName("GroupBy desconhecido - usa origin como fallback")
        void unknownGroupBy_shouldFallbackToOrigin() {
            when(reportRepository.revenueByOrigin(FROM, TO)).thenReturn(Collections.emptyList());

            service.getRevenue(FROM, TO, "unknown_group");

            verify(reportRepository).revenueByOrigin(FROM, TO);
        }

        @Test
        @DisplayName("Sem dados - retorna lista vazia")
        void noData_shouldReturnEmptyList() {
            when(reportRepository.revenueByMethod(FROM, TO)).thenReturn(Collections.emptyList());

            List<RevenueReportEntry> result = service.getRevenue(FROM, TO, "method");

            assertThat(result).isEmpty();
        }
    }

    // ==================== MRR ====================

    @Nested
    @DisplayName("getMrr()")
    class MrrTests {

        @Test
        @DisplayName("MRR calcula corretamente MRR e ARR")
        void mrr_shouldCalculateCorrectly() {
            when(reportRepository.calculateMrr()).thenReturn(new BigDecimal("10000.00"));
            when(reportRepository.countActiveSubscriptions()).thenReturn(50L);

            MrrReport result = service.getMrr();

            assertThat(result.mrr()).isEqualByComparingTo(new BigDecimal("10000.00"));
            assertThat(result.arr()).isEqualByComparingTo(new BigDecimal("120000.00")); // 10000 * 12
            assertThat(result.activeSubscriptions()).isEqualTo(50);
            assertThat(result.calculatedAt()).isNotNull();
        }

        @Test
        @DisplayName("MRR sem assinaturas ativas - zero")
        void noSubscriptions_shouldReturnZero() {
            when(reportRepository.calculateMrr()).thenReturn(BigDecimal.ZERO);
            when(reportRepository.countActiveSubscriptions()).thenReturn(0L);

            MrrReport result = service.getMrr();

            assertThat(result.mrr()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.arr()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.activeSubscriptions()).isZero();
        }
    }

    // ==================== CHURN ====================

    @Nested
    @DisplayName("getChurn()")
    class ChurnTests {

        @Test
        @DisplayName("Churn rate calculado corretamente")
        void churnRate_shouldCalculateCorrectly() {
            when(reportRepository.countCanceledInPeriod(FROM, TO)).thenReturn(5L);
            when(reportRepository.countActiveAtDate(FROM)).thenReturn(100L);

            ChurnReport result = service.getChurn(FROM, TO);

            assertThat(result.canceledInPeriod()).isEqualTo(5);
            assertThat(result.activeAtStart()).isEqualTo(100);
            // 5/100 * 100 = 5.00%
            assertThat(result.churnRate()).isEqualByComparingTo(new BigDecimal("5.00"));
            assertThat(result.periodStart()).isEqualTo(FROM);
            assertThat(result.periodEnd()).isEqualTo(TO);
        }

        @Test
        @DisplayName("Churn zero quando nenhum cancelamento")
        void noCancellations_shouldReturnZeroChurn() {
            when(reportRepository.countCanceledInPeriod(FROM, TO)).thenReturn(0L);
            when(reportRepository.countActiveAtDate(FROM)).thenReturn(100L);

            ChurnReport result = service.getChurn(FROM, TO);

            assertThat(result.churnRate()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Churn zero quando nenhuma assinatura ativa no inicio (divisao por zero)")
        void noActiveAtStart_shouldReturnZeroChurn() {
            when(reportRepository.countCanceledInPeriod(FROM, TO)).thenReturn(5L);
            when(reportRepository.countActiveAtDate(FROM)).thenReturn(0L);

            ChurnReport result = service.getChurn(FROM, TO);

            assertThat(result.churnRate()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Churn 100% quando todos cancelaram")
        void allCanceled_shouldReturn100Percent() {
            when(reportRepository.countCanceledInPeriod(FROM, TO)).thenReturn(50L);
            when(reportRepository.countActiveAtDate(FROM)).thenReturn(50L);

            ChurnReport result = service.getChurn(FROM, TO);

            assertThat(result.churnRate()).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("Churn com valor fracionario arredondado para 2 casas")
        void fractionalChurn_shouldRoundTo2Decimals() {
            when(reportRepository.countCanceledInPeriod(FROM, TO)).thenReturn(3L);
            when(reportRepository.countActiveAtDate(FROM)).thenReturn(7L);

            ChurnReport result = service.getChurn(FROM, TO);

            // 3/7 * 100 = 42.857... -> 42.86
            assertThat(result.churnRate()).isEqualByComparingTo(new BigDecimal("42.86"));
            assertThat(result.churnRate().scale()).isEqualTo(2);
        }
    }

    // ==================== OVERDUE ====================

    @Nested
    @DisplayName("getOverdue()")
    class OverdueTests {

        @Test
        @DisplayName("Retorna lista de clientes inadimplentes")
        void overdue_shouldReturnCustomerSummary() {
            Object[] row1 = new Object[]{10L, "Joao Silva", 3L, new BigDecimal("450.00")};
            Object[] row2 = new Object[]{20L, "Maria Santos", 1L, new BigDecimal("100.00")};
            when(reportRepository.findOverdueSummary()).thenReturn(List.<Object[]>of(row1, row2));

            List<OverdueReportEntry> result = service.getOverdue();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).customerId()).isEqualTo(10L);
            assertThat(result.get(0).customerName()).isEqualTo("Joao Silva");
            assertThat(result.get(0).overdueCount()).isEqualTo(3);
            assertThat(result.get(0).totalOverdueValue()).isEqualByComparingTo(new BigDecimal("450.00"));
        }

        @Test
        @DisplayName("Sem inadimplencia - lista vazia")
        void noOverdue_shouldReturnEmptyList() {
            when(reportRepository.findOverdueSummary()).thenReturn(Collections.emptyList());

            List<OverdueReportEntry> result = service.getOverdue();

            assertThat(result).isEmpty();
        }
    }
}
