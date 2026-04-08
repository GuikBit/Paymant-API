package br.com.holding.payments.integration.asaas.gateway;

import java.math.BigDecimal;

public record AsaasPaymentResult(
        String asaasId,
        String customerAsaasId,
        String billingType,
        BigDecimal value,
        BigDecimal netValue,
        String status,
        String dueDate,
        String paymentDate,
        String invoiceUrl,
        String bankSlipUrl,
        String installmentId,
        Integer installmentNumber
) {}
