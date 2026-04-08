package br.com.holding.payments.integration.asaas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsaasRefundRequest(
        BigDecimal value,
        String description
) {}
