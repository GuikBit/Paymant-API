package br.com.holding.payments.charge;

import java.util.Map;
import java.util.Set;

public enum ChargeStatus {
    PENDING,
    CONFIRMED,
    RECEIVED,
    OVERDUE,
    REFUNDED,
    CHARGEBACK,
    CANCELED;

    private static final Map<ChargeStatus, Set<ChargeStatus>> ALLOWED = Map.of(
            PENDING, Set.of(CONFIRMED, RECEIVED, OVERDUE, CANCELED),
            CONFIRMED, Set.of(RECEIVED, REFUNDED, CHARGEBACK, CANCELED),
            RECEIVED, Set.of(REFUNDED, CHARGEBACK),
            OVERDUE, Set.of(RECEIVED, CONFIRMED, CANCELED),
            REFUNDED, Set.of(),
            CHARGEBACK, Set.of(REFUNDED),
            CANCELED, Set.of()
    );

    public boolean canTransitionTo(ChargeStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }
}
