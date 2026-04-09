package br.com.holding.payments.planchange.dto;

import br.com.holding.payments.planchange.PlanChangePolicy;
import br.com.holding.payments.planchange.PlanChangeType;

import java.math.BigDecimal;

public record PlanChangePreviewResponse(
        Long subscriptionId,
        Long currentPlanId,
        String currentPlanName,
        BigDecimal currentPlanValue,
        Long newPlanId,
        String newPlanName,
        BigDecimal newPlanValue,
        PlanChangeType changeType,
        PlanChangePolicy policy,
        BigDecimal delta,
        BigDecimal prorationCredit,
        BigDecimal prorationCharge
) {}
