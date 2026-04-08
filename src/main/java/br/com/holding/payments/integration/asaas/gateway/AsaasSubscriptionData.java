package br.com.holding.payments.integration.asaas.gateway;

import java.math.BigDecimal;

public record AsaasSubscriptionData(
        String customerAsaasId,
        String billingType,
        BigDecimal value,
        String nextDueDate,
        String cycle,
        String description,
        String externalReference,
        AsaasPaymentData.CreditCardData creditCard,
        AsaasPaymentData.CreditCardHolderData creditCardHolderInfo,
        String creditCardToken,
        String remoteIp
) {}
