package br.com.holding.payments.planchange;

import br.com.holding.payments.tenant.CrossTenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledPlanChangeJob {

    private final PlanChangeService planChangeService;

    @Scheduled(cron = "${app.plan-change.scheduled-cron:0 0 1 * * *}")
    @CrossTenant(reason = "Job diario de processamento de mudancas agendadas")
    public void processScheduledChanges() {
        log.info("Starting scheduled plan changes processing");
        planChangeService.processScheduledChanges();
        log.info("Scheduled plan changes processing complete");
    }
}
