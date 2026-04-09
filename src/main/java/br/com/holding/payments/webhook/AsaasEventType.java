package br.com.holding.payments.webhook;

import br.com.holding.payments.charge.ChargeStatus;

import java.util.Map;

public enum AsaasEventType {

    PAYMENT_CREATED,
    PAYMENT_AWAITING_RISK_ANALYSIS,
    PAYMENT_APPROVED_BY_RISK_ANALYSIS,
    PAYMENT_REPROVED_BY_RISK_ANALYSIS,
    PAYMENT_AUTHORIZED,
    PAYMENT_UPDATED,
    PAYMENT_CONFIRMED,
    PAYMENT_RECEIVED,
    PAYMENT_OVERDUE,
    PAYMENT_DELETED,
    PAYMENT_RESTORED,
    PAYMENT_REFUNDED,
    PAYMENT_CHARGEBACK_REQUESTED,
    PAYMENT_CHARGEBACK_DISPUTE,
    PAYMENT_DUNNING_RECEIVED,
    SUBSCRIPTION_CREATED,
    SUBSCRIPTION_UPDATED,
    SUBSCRIPTION_DELETED;

    private static final Map<AsaasEventType, ChargeStatus> CHARGE_STATUS_MAP = Map.ofEntries(
            Map.entry(PAYMENT_CONFIRMED, ChargeStatus.CONFIRMED),
            Map.entry(PAYMENT_RECEIVED, ChargeStatus.RECEIVED),
            Map.entry(PAYMENT_OVERDUE, ChargeStatus.OVERDUE),
            Map.entry(PAYMENT_DELETED, ChargeStatus.CANCELED),
            Map.entry(PAYMENT_REFUNDED, ChargeStatus.REFUNDED),
            Map.entry(PAYMENT_CHARGEBACK_REQUESTED, ChargeStatus.CHARGEBACK),
            Map.entry(PAYMENT_CHARGEBACK_DISPUTE, ChargeStatus.CHARGEBACK)
    );

    public boolean isPaymentEvent() {
        return name().startsWith("PAYMENT_");
    }

    public boolean isSubscriptionEvent() {
        return name().startsWith("SUBSCRIPTION_");
    }

    public boolean triggersChargeTransition() {
        return CHARGE_STATUS_MAP.containsKey(this);
    }

    public ChargeStatus toChargeStatus() {
        return CHARGE_STATUS_MAP.get(this);
    }
}
