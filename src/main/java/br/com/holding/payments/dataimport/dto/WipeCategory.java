package br.com.holding.payments.dataimport.dto;

public enum WipeCategory {
    CUSTOMERS,
    SUBSCRIPTIONS,
    PLANS,
    CHARGES,
    INSTALLMENTS,
    PLAN_CHANGES,
    CREDIT_LEDGER,
    WEBHOOK_EVENTS,
    OUTBOX,
    IDEMPOTENCY_KEYS,
    AUDIT_LOG
}
