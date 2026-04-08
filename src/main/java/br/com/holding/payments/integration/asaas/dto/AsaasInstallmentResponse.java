package br.com.holding.payments.integration.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasInstallmentResponse(
        String id,
        String customer,
        String billingType,
        BigDecimal value,
        Integer installmentCount,
        String status,
        String externalReference,
        String dateCreated
) {}
