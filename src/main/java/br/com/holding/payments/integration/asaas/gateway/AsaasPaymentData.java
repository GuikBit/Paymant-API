package br.com.holding.payments.integration.asaas.gateway;

import java.math.BigDecimal;

public record AsaasPaymentData(
        String customerAsaasId,
        String billingType,
        BigDecimal value,
        String dueDate,
        String description,
        String externalReference,
        Integer installmentCount,
        BigDecimal installmentValue,
        CreditCardData creditCard,
        CreditCardHolderData creditCardHolderInfo,
        String creditCardToken,
        String remoteIp
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
