package br.com.holding.payments.accesspolicy.dto;

import jakarta.validation.constraints.Min;

public record UpdateAccessPolicyRequest(
        @Min(1) Integer maxOverdueCharges,
        @Min(0) Integer overdueToleranceDays,
        Boolean blockOnSuspendedSubscription,
        Boolean blockOnStandaloneCharges,
        Boolean blockOnSubscriptionCharges,
        Boolean blockOnNegativeCredit,
        String customBlockMessage,
        @Min(1) Integer cacheTtlMinutes
) {}
