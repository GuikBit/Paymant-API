-- =============================================================
-- V9: Add is_free flag to plans
--
-- Planos gratuitos (is_free=TRUE) nao criam subscription no Asaas.
-- A assinatura existe apenas internamente ate que o cliente migre
-- para um plano pago via plan change. Nesse momento, a subscription
-- e criada no Asaas (sem trial automatico).
--
-- Subscriptions de plano gratuito carregam asaas_id = NULL, por
-- isso a coluna subscriptions.asaas_id ja e nullable (desde V1).
-- =============================================================

ALTER TABLE plans
    ADD COLUMN is_free BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_plans_is_free ON plans (is_free) WHERE is_free = TRUE;
