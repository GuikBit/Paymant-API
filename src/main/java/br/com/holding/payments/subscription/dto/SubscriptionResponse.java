package br.com.holding.payments.subscription.dto;

import br.com.holding.payments.charge.BillingType;
import br.com.holding.payments.subscription.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SubscriptionResponse(
        Long id,
        Long companyId,
        Long customerId,
        Long planId,
        String planName,
        String asaasId,
        BillingType billingType,
        BigDecimal value,
        String cycle,
        LocalDateTime currentPeriodStart,
        LocalDateTime currentPeriodEnd,
        LocalDate nextDueDate,
        SubscriptionStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
