package br.com.holding.payments.integration.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasSubscriptionResponse(
        String id,
        String customer,
        String billingType,
        BigDecimal value,
        String nextDueDate,
        String cycle,
        String description,
        String externalReference,
        String status,
        Boolean deleted,
        String dateCreated
) {}
