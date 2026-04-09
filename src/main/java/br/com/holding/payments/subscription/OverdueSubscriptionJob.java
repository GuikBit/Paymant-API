package br.com.holding.payments.subscription;

import br.com.holding.payments.tenant.CrossTenant;
import br.com.holding.payments.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OverdueSubscriptionJob {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    @Value("${app.subscription.max-overdue-charges:3}")
    private int maxOverdueCharges;

    @Scheduled(cron = "${app.subscription.overdue-check-cron:0 0 8 * * *}")
    @CrossTenant(reason = "Job de verificacao de inadimplencia cross-tenant")
    @Transactional
    public void handleOverdueSubscriptions() {
        log.info("Starting overdue subscription check (threshold: {} overdue charges)", maxOverdueCharges);

        List<Subscription> overdueSubscriptions =
                subscriptionRepository.findActiveWithOverdueCharges(maxOverdueCharges);

        if (overdueSubscriptions.isEmpty()) {
            log.info("No overdue subscriptions found");
            return;
        }

        int suspended = 0;
        for (Subscription subscription : overdueSubscriptions) {
            try {
                TenantContext.setCompanyId(subscription.getCompany().getId());
                subscriptionService.suspend(subscription.getId(),
                        "Suspensao automatica por " + maxOverdueCharges + " cobrancas vencidas");
                suspended++;
            } catch (Exception e) {
                log.error("Failed to suspend subscription id={}: {}",
                        subscription.getId(), e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }

        log.info("Overdue subscription check complete: {}/{} subscriptions suspended",
                suspended, overdueSubscriptions.size());
    }
}
