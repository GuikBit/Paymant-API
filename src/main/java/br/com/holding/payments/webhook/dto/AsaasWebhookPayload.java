package br.com.holding.payments.webhook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasWebhookPayload(
        String id,
        String event,
        Payment payment,
        SubscriptionData subscription
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payment(
            String id,
            String customer,
            String subscription,
            String installment,
            String billingType,
            BigDecimal value,
            String dueDate,
            String status,
            String externalReference,
            String invoiceUrl,
            String bankSlipUrl,
            String description
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubscriptionData(
            String id,
            String customer,
            String billingType,
            BigDecimal value,
            String nextDueDate,
            String cycle,
            String status,
            boolean deleted
    ) {}
}
