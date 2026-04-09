package br.com.holding.payments.planchange.dto;

import br.com.holding.payments.planchange.PlanChangePolicy;
import br.com.holding.payments.planchange.PlanChangeStatus;
import br.com.holding.payments.planchange.PlanChangeType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PlanChangeResponse(
        Long id,
        Long subscriptionId,
        Long previousPlanId,
        String previousPlanName,
        Long requestedPlanId,
        String requestedPlanName,
        PlanChangeType changeType,
        PlanChangePolicy policy,
        BigDecimal deltaAmount,
        BigDecimal prorationCredit,
        BigDecimal prorationCharge,
        PlanChangeStatus status,
        Long chargeId,
        Long creditLedgerId,
        LocalDateTime scheduledFor,
        LocalDateTime effectiveAt,
        String requestedBy,
        LocalDateTime requestedAt,
        String failureReason
) {}
