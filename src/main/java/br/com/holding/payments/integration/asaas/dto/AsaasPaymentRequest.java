package br.com.holding.payments.integration.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasPaymentRequest(
        String customer,
        String billingType,
        BigDecimal value,
        String dueDate,
        String description,
        String externalReference,
        Integer installmentCount,
        BigDecimal installmentValue,
        BigDecimal discount,
        BigDecimal interest,
        BigDecimal fine,
        Boolean postalService,
        AsaasCreditCardRequest creditCard,
        AsaasCreditCardHolderInfoRequest creditCardHolderInfo,
        String creditCardToken,
        String remoteIp
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AsaasCreditCardRequest(
            String holderName,
            String number,
            String expiryMonth,
            String expiryYear,
            String ccv
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AsaasCreditCardHolderInfoRequest(
            String name,
            String email,
            String cpfCnpj,
            String postalCode,
            String addressNumber,
            String addressComplement,
            String phone,
            String mobilePhone
    ) {}
}
