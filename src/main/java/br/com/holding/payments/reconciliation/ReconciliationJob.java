package br.com.holding.payments.reconciliation;

import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.company.CompanyStatus;
import br.com.holding.payments.reconciliation.dto.ReconciliationResult;
import br.com.holding.payments.tenant.CrossTenant;
import br.com.holding.payments.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationJob {

    private final ReconciliationService reconciliationService;
    private final CompanyRepository companyRepository;

    /**
     * Runs daily at 3 AM: reconciles charges from the last 3 days
     * and subscriptions for all active companies.
     */
    @Scheduled(cron = "${app.reconciliation.cron:0 0 3 * * *}")
    @CrossTenant(reason = "Reconciliation job runs across all companies")
    public void runDailyReconciliation() {
        log.info("Starting daily reconciliation job");

        List<Company> companies = companyRepository.findAll();
        LocalDate since = LocalDate.now().minusDays(3);

        for (Company company : companies) {
            if (company.getStatus() != CompanyStatus.ACTIVE) continue;

            try {
                TenantContext.setCompanyId(company.getId());

                ReconciliationResult chargeResult = reconciliationService.reconcileChargesSince(
                        company.getId(), since);
                ReconciliationResult subResult = reconciliationService.reconcileSubscriptions(
                        company.getId());

                log.info("Reconciliation for company={}: charges(checked={}, divergences={}), " +
                                "subscriptions(checked={}, divergences={})",
                        company.getId(),
                        chargeResult.totalChecked(), chargeResult.divergencesFound(),
                        subResult.totalChecked(), subResult.divergencesFound());
            } catch (Exception e) {
                log.error("Reconciliation failed for company={}: {}", company.getId(), e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }

        // Also replay DLQ
        try {
            reconciliationService.replayDLQ();
        } catch (Exception e) {
            log.error("DLQ replay failed: {}", e.getMessage(), e);
        }

        log.info("Daily reconciliation job complete");
    }
}
