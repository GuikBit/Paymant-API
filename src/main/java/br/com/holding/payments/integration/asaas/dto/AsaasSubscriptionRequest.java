package br.com.holding.payments.integration.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasSubscriptionRequest(
        String customer,
        String billingType,
        BigDecimal value,
        String nextDueDate,
        String cycle,
        String description,
        String externalReference,
        BigDecimal discount,
        BigDecimal interest,
        BigDecimal fine,
        AsaasPaymentRequest.AsaasCreditCardRequest creditCard,
        AsaasPaymentRequest.AsaasCreditCardHolderInfoRequest creditCardHolderInfo,
        String creditCardToken,
        String remoteIp
) {}
