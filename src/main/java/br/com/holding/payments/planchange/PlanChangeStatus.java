package br.com.holding.payments.planchange;

import java.util.Map;
import java.util.Set;

public enum PlanChangeStatus {
    PENDING,
    AWAITING_PAYMENT,
    EFFECTIVE,
    SCHEDULED,
    FAILED,
    CANCELED;

    private static final Map<PlanChangeStatus, Set<PlanChangeStatus>> ALLOWED = Map.of(
            PENDING, Set.of(AWAITING_PAYMENT, EFFECTIVE, SCHEDULED, FAILED, CANCELED),
            AWAITING_PAYMENT, Set.of(EFFECTIVE, FAILED, CANCELED),
            SCHEDULED, Set.of(EFFECTIVE, FAILED, CANCELED),
            EFFECTIVE, Set.of(),
            FAILED, Set.of(),
            CANCELED, Set.of()
    );

    public boolean canTransitionTo(PlanChangeStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }
}
