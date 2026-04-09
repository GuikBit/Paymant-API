package br.com.holding.payments.subscription;

import java.util.Map;
import java.util.Set;

public enum SubscriptionStatus {
    ACTIVE,
    PAUSED,
    SUSPENDED,
    CANCELED,
    EXPIRED;

    private static final Map<SubscriptionStatus, Set<SubscriptionStatus>> ALLOWED = Map.of(
            ACTIVE, Set.of(PAUSED, SUSPENDED, CANCELED, EXPIRED),
            PAUSED, Set.of(ACTIVE, CANCELED),
            SUSPENDED, Set.of(ACTIVE, CANCELED),
            CANCELED, Set.of(),
            EXPIRED, Set.of()
    );

    public boolean canTransitionTo(SubscriptionStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }
}
