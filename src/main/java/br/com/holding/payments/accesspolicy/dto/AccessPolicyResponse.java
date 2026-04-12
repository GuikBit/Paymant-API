package br.com.holding.payments.accesspolicy.dto;

import java.time.LocalDateTime;

public record AccessPolicyResponse(
        Long id,
        Long companyId,
        Integer maxOverdueCharges,
        Integer overdueToleranceDays,
        Boolean blockOnSuspendedSubscription,
        Boolean blockOnStandaloneCharges,
        Boolean blockOnSubscriptionCharges,
        Boolean blockOnNegativeCredit,
        String customBlockMessage,
        Integer cacheTtlMinutes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
