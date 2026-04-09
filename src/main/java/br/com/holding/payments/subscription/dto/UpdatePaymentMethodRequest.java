package br.com.holding.payments.subscription.dto;

import br.com.holding.payments.charge.BillingType;
import jakarta.validation.constraints.NotNull;

public record UpdatePaymentMethodRequest(
        @NotNull BillingType billingType,
        CreateSubscriptionRequest.CreditCardData creditCard,
        CreateSubscriptionRequest.CreditCardHolderData creditCardHolderInfo,
        String creditCardToken,
        String remoteIp
) {}
