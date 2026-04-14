package br.com.holding.payments.integration.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasPaymentResponse(
        String id,
        String customer,
        String billingType,
        BigDecimal value,
        BigDecimal netValue,
        String status,
        String dueDate,
        String originalDueDate,
        String paymentDate,
        String confirmedDate,
        String description,
        String externalReference,
        String invoiceUrl,
        String bankSlipUrl,
        String transactionReceiptUrl,
        String installment,
        Integer installmentNumber,
        String subscription,
        Boolean deleted,
        String dateCreated
) {}
