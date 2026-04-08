package br.com.holding.payments.plan.dto;

import br.com.holding.payments.plan.PlanCycle;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PlanResponse(
        Long id,
        Long companyId,
        String name,
        String description,
        BigDecimal value,
        PlanCycle cycle,
        Integer trialDays,
        BigDecimal setupFee,
        Boolean active,
        Integer version,
        String limits,
        String features,
        Integer tierOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
