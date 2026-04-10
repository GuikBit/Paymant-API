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
        BigDecimal effectivePrice,
        String cycle,
        LocalDateTime currentPeriodStart,
        LocalDateTime currentPeriodEnd,
        LocalDate nextDueDate,
        SubscriptionStatus status,
        String couponCode,
        BigDecimal couponDiscountAmount,
        Integer couponUsesRemaining,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
