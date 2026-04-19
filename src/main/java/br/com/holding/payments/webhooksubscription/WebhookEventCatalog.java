package br.com.holding.payments.webhooksubscription;

import java.util.Arrays;
import java.util.List;

public enum WebhookEventCatalog {
    CustomerCreatedEvent("Customer", "Novo cliente cadastrado"),
    ChargeCreatedEvent("Charge", "Cobranca gerada (PIX, boleto, cartao ou indefinido)"),
    ChargePaidEvent("Charge", "Cobranca confirmada ou recebida"),
    ChargeCanceledEvent("Charge", "Cobranca cancelada"),
    ChargeRefundedEvent("Charge", "Cobranca estornada (total ou parcial)"),
    SubscriptionCreatedEvent("Subscription", "Assinatura criada"),
    SubscriptionPausedEvent("Subscription", "Assinatura pausada"),
    SubscriptionResumedEvent("Subscription", "Assinatura retomada"),
    SubscriptionSuspendedEvent("Subscription", "Assinatura suspensa por inadimplencia"),
    SubscriptionCanceledEvent("Subscription", "Assinatura cancelada"),
    PlanChangeScheduledEvent("SubscriptionPlanChange", "Mudanca de plano agendada para fim do ciclo"),
    PlanChangedEvent("SubscriptionPlanChange", "Mudanca de plano efetivada"),
    PlanChangePendingPaymentEvent("SubscriptionPlanChange", "Mudanca aguardando pagamento do pro-rata"),
    WebhookTestEvent("Test", "Evento de teste disparado via botao de ping");

    public static final String WILDCARD = "*";

    private final String resourceType;
    private final String description;

    WebhookEventCatalog(String resourceType, String description) {
        this.resourceType = resourceType;
        this.description = description;
    }

    public String resourceType() { return resourceType; }
    public String description() { return description; }

    public static boolean isPublic(String eventType) {
        if (WILDCARD.equals(eventType)) return true;
        for (WebhookEventCatalog e : values()) {
            if (e.name().equals(eventType)) return true;
        }
        return false;
    }

    public static List<WebhookEventCatalog> all() {
        return Arrays.asList(values());
    }
}
