package br.com.holding.payments.subscription.dto;

import br.com.holding.payments.charge.BillingType;
import br.com.holding.payments.plan.PlanCycle;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateSubscriptionRequest(
        @NotNull Long customerId,
        @NotNull Long planId,
        @NotNull BillingType billingType,
        @NotNull(message = "Ciclo de cobranca e obrigatorio") PlanCycle cycle,
        LocalDate nextDueDate,
        String description,
        String externalReference,
        CreditCardData creditCard,
        CreditCardHolderData creditCardHolderInfo,
        String creditCardToken,
        String remoteIp,
        String couponCode
) {

    public record CreditCardData(
            String holderName,
            String number,
            String expiryMonth,
            String expiryYear,
            String ccv
    ) {}

    public record CreditCardHolderData(
            String name,
            String email,
            String cpfCnpj,
            String postalCode,
            String addressNumber,
            String phone
    ) {}
}
