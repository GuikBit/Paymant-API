package br.com.holding.payments.subscription.dto;

import br.com.holding.payments.charge.BillingType;

import java.time.LocalDate;

public record UpdateSubscriptionRequest(
        BillingType billingType,
        LocalDate nextDueDate,
        String description,
        String externalReference
) {}
