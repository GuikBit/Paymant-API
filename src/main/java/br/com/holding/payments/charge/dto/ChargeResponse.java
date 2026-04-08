package br.com.holding.payments.charge.dto;

import br.com.holding.payments.charge.BillingType;
import br.com.holding.payments.charge.ChargeOrigin;
import br.com.holding.payments.charge.ChargeStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ChargeResponse(
        Long id,
        Long companyId,
        Long customerId,
        Long subscriptionId,
        Long installmentId,
        String asaasId,
        BillingType billingType,
        BigDecimal value,
        LocalDate dueDate,
        ChargeStatus status,
        ChargeOrigin origin,
        String externalReference,
        String pixQrcode,
        String pixCopyPaste,
        String boletoUrl,
        String invoiceUrl,
        Integer installmentNumber,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
