package br.com.holding.payments.planchange;

import br.com.holding.payments.planchange.dto.PlanChangeResponse;
import org.springframework.stereotype.Component;

@Component
public class PlanChangeMapper {

    public PlanChangeResponse toResponse(SubscriptionPlanChange change) {
        return new PlanChangeResponse(
                change.getId(),
                change.getSubscription().getId(),
                change.getPreviousPlan().getId(),
                change.getPreviousPlan().getName(),
                change.getRequestedPlan().getId(),
                change.getRequestedPlan().getName(),
                change.getChangeType(),
                change.getPolicy(),
                change.getDeltaAmount(),
                change.getProrationCredit(),
                change.getProrationCharge(),
                change.getStatus(),
                change.getCharge() != null ? change.getCharge().getId() : null,
                change.getCreditLedgerEntry() != null ? change.getCreditLedgerEntry().getId() : null,
                change.getScheduledFor(),
                change.getEffectiveAt(),
                change.getRequestedBy(),
                change.getRequestedAt(),
                change.getFailureReason()
        );
    }
}
