package br.com.holding.payments.phase9;

import br.com.holding.payments.report.ReportExportController;
import br.com.holding.payments.report.ReportService;
import br.com.holding.payments.report.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Fase 9 - ReportExportController (CSV)")
class ReportExportControllerTest {

    @Mock
    private ReportService reportService;

    @InjectMocks
    private ReportExportController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Export revenue CSV - headers corretos e conteudo")
    void exportRevenue_shouldReturnCsvWithCorrectHeaders() throws Exception {
        when(reportService.getRevenue(any(), any(), eq("origin")))
                .thenReturn(List.of(
                        new RevenueReportEntry("API", 10, new BigDecimal("1500.00")),
                        new RevenueReportEntry("WEB", 5, new BigDecimal("800.00"))
                ));

        mockMvc.perform(get("/api/v1/reports/export/revenue")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31")
                        .param("groupBy", "origin"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("revenue_")))
                .andExpect(header().string("Content-Disposition", containsString(".csv")))
                .andExpect(content().string(containsString("Agrupamento,Quantidade,Valor Total")))
                .andExpect(content().string(containsString("API,10,1500.00")))
                .andExpect(content().string(containsString("WEB,5,800.00")));
    }

    @Test
    @DisplayName("Export MRR CSV - headers e conteudo")
    void exportMrr_shouldReturnCsv() throws Exception {
        when(reportService.getMrr())
                .thenReturn(new MrrReport(
                        LocalDateTime.of(2026, 4, 9, 10, 0),
                        50, new BigDecimal("10000.00"), new BigDecimal("120000.00")));

        mockMvc.perform(get("/api/v1/reports/export/mrr"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("mrr_report.csv")))
                .andExpect(content().string(containsString("Calculado Em,Assinaturas Ativas,MRR,ARR")))
                .andExpect(content().string(containsString("50")))
                .andExpect(content().string(containsString("10000.00")))
                .andExpect(content().string(containsString("120000.00")));
    }

    @Test
    @DisplayName("Export churn CSV - headers e conteudo")
    void exportChurn_shouldReturnCsv() throws Exception {
        when(reportService.getChurn(any(), any()))
                .thenReturn(new ChurnReport(
                        LocalDateTime.now(),
                        LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                        5, 100, new BigDecimal("5.00")));

        mockMvc.perform(get("/api/v1/reports/export/churn")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("churn_")))
                .andExpect(content().string(containsString("Periodo Inicio,Periodo Fim,Cancelamentos,Ativas no Inicio,Taxa Churn (%)")))
                .andExpect(content().string(containsString("2026-03-01")))
                .andExpect(content().string(containsString("5.00")));
    }

    @Test
    @DisplayName("Export overdue CSV - headers e conteudo com aspas em nomes")
    void exportOverdue_shouldReturnCsvWithQuotedNames() throws Exception {
        when(reportService.getOverdue())
                .thenReturn(List.of(
                        new OverdueReportEntry(10L, "Joao Silva", 3, new BigDecimal("450.00")),
                        new OverdueReportEntry(20L, "Maria Santos", 1, new BigDecimal("100.00"))
                ));

        mockMvc.perform(get("/api/v1/reports/export/overdue"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("overdue_report.csv")))
                .andExpect(content().string(containsString("ID Cliente,Nome Cliente")))
                .andExpect(content().string(containsString("\"Joao Silva\"")))
                .andExpect(content().string(containsString("450.00")));
    }

    @Test
    @DisplayName("Export revenue sem dados - apenas header CSV")
    void exportRevenueNoData_shouldReturnOnlyHeader() throws Exception {
        when(reportService.getRevenue(any(), any(), eq("method")))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/reports/export/revenue")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31")
                        .param("groupBy", "method"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Agrupamento,Quantidade,Valor Total")));
    }
}
