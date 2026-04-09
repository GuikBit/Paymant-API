package br.com.holding.payments.planchange.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record RequestPlanChangeRequest(
        @NotNull Long newPlanId,
        Map<String, Integer> currentUsage,
        String requestedBy
) {}
