-- =============================================================
-- V10: Webhook subscriptions por tenant
--
-- Permite que cada company cadastre N URLs para receber eventos
-- de dominio (ChargeCreated, PaymentReceived, etc). Token bruto
-- so e mostrado na criacao; depois so o prefixo + hash ficam.
-- =============================================================

CREATE TABLE webhook_subscriptions (
    id               BIGSERIAL      PRIMARY KEY,
    company_id       BIGINT         NOT NULL REFERENCES companies(id),
    url              TEXT           NOT NULL,
    name             VARCHAR(100)   NOT NULL,
    description      VARCHAR(500),
    token_encrypted  TEXT           NOT NULL,
    token_prefix     VARCHAR(12)    NOT NULL,
    event_types      TEXT[]         NOT NULL,
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    last_delivery_at TIMESTAMP,
    last_success_at  TIMESTAMP,
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_subs_company_active
    ON webhook_subscriptions (company_id, active) WHERE active = TRUE;
CREATE INDEX idx_webhook_subs_event_types
    ON webhook_subscriptions USING GIN (event_types);

-- RLS
ALTER TABLE webhook_subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE webhook_subscriptions FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON webhook_subscriptions
    USING (
        company_id = current_setting('app.current_company_id', true)::bigint
        OR current_setting('app.bypass_rls', true) = 'true'
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON webhook_subscriptions TO payment_app;
GRANT USAGE, SELECT ON SEQUENCE webhook_subscriptions_id_seq TO payment_app;
GRANT ALL ON webhook_subscriptions TO payment_flyway;

-- =============================================================
-- Tabela de tentativas de entrega (historico + retry)
-- =============================================================

CREATE TABLE webhook_delivery_attempts (
    id                     BIGSERIAL      PRIMARY KEY,
    company_id             BIGINT         NOT NULL REFERENCES companies(id),
    subscription_id        BIGINT         NOT NULL REFERENCES webhook_subscriptions(id) ON DELETE CASCADE,
    outbox_event_id        BIGINT,
    event_type             VARCHAR(100)   NOT NULL,
    event_id               VARCHAR(100)   NOT NULL,
    url                    TEXT           NOT NULL,
    request_body           TEXT,
    response_status        INT,
    response_body_excerpt  TEXT,
    duration_ms            BIGINT,
    attempt_number         INT            NOT NULL DEFAULT 1,
    status                 VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    error_message          TEXT,
    next_attempt_at        TIMESTAMP,
    created_at             TIMESTAMP      NOT NULL DEFAULT NOW(),
    delivered_at           TIMESTAMP
);

CREATE INDEX idx_webhook_deliveries_ready
    ON webhook_delivery_attempts (status, next_attempt_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_webhook_deliveries_subscription
    ON webhook_delivery_attempts (subscription_id, created_at DESC);
CREATE INDEX idx_webhook_deliveries_event
    ON webhook_delivery_attempts (event_id);

-- RLS
ALTER TABLE webhook_delivery_attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE webhook_delivery_attempts FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON webhook_delivery_attempts
    USING (
        company_id = current_setting('app.current_company_id', true)::bigint
        OR current_setting('app.bypass_rls', true) = 'true'
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON webhook_delivery_attempts TO payment_app;
GRANT USAGE, SELECT ON SEQUENCE webhook_delivery_attempts_id_seq TO payment_app;
GRANT ALL ON webhook_delivery_attempts TO payment_flyway;
