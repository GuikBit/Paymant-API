package br.com.holding.payments.plan.dto;

import br.com.holding.payments.plan.PlanCycle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreatePlanRequest(
        @NotBlank(message = "Nome do plano e obrigatorio") String name,
        String description,
        @NotNull(message = "Valor e obrigatorio") @Positive(message = "Valor deve ser positivo") BigDecimal value,
        @NotNull(message = "Ciclo e obrigatorio") PlanCycle cycle,
        Integer trialDays,
        BigDecimal setupFee,
        String limits,
        String features,
        Integer tierOrder
) {}
