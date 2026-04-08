package br.com.holding.payments.plan.dto;

import java.math.BigDecimal;

public record UpdatePlanRequest(
        String name,
        String description,
        BigDecimal value,
        Integer trialDays,
        BigDecimal setupFee,
        String limits,
        String features,
        Integer tierOrder
) {}
