package br.com.holding.payments.integration.asaas.gateway;

import java.math.BigDecimal;

public record AsaasSubscriptionResult(
        String asaasId,
        String customerAsaasId,
        String billingType,
        BigDecimal value,
        String nextDueDate,
        String cycle,
        String status
) {}
