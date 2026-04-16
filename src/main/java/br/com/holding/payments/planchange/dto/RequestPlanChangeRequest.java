package br.com.holding.payments.planchange.dto;

import br.com.holding.payments.charge.BillingType;
import br.com.holding.payments.subscription.dto.CreateSubscriptionRequest;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record RequestPlanChangeRequest(
        @NotNull Long newPlanId,
        Map<String, Integer> currentUsage,
        String requestedBy,
        // Campos usados apenas na promocao de plano gratuito para pago.
        // Para trocas entre planos pagos, sao ignorados (Asaas usa o billing method ja configurado).
        BillingType billingType,
        CreateSubscriptionRequest.CreditCardData creditCard,
        CreateSubscriptionRequest.CreditCardHolderData creditCardHolderInfo,
        String creditCardToken,
        String remoteIp
) {}
