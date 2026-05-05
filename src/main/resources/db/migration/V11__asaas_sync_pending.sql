-- =============================================================
-- V11: Asaas sync pending queue
--
-- Fila para retry assincrono de operacoes de sincronizacao com a
-- API do Asaas que falharam apos os retries sincronos do
-- Resilience4j. Usado para garantir que mudancas de plano que
-- alteram o valor recorrente da assinatura no Asaas sejam
-- propagadas mesmo quando a chamada inicial falha (rede, 5xx,
-- token invalido temporariamente, etc.).
--
-- Status: PENDING -> DONE (sucesso) ou DEAD (max_attempts atingido)
-- =============================================================

CREATE TABLE asaas_sync_pending (
    id                BIGSERIAL      PRIMARY KEY,
    company_id        BIGINT         NOT NULL REFERENCES companies(id),
    subscription_id   BIGINT         NOT NULL REFERENCES subscriptions(id),
    asaas_id          VARCHAR(100)   NOT NULL,
    operation         VARCHAR(40)    NOT NULL,
    target_value      NUMERIC(12,2),
    correlation_id    VARCHAR(100),
    status            VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    attempts          INT            NOT NULL DEFAULT 0,
    max_attempts      INT            NOT NULL DEFAULT 8,
    next_attempt_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    last_error        TEXT,
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMP
);

CREATE INDEX idx_asaas_sync_pending_ready
    ON asaas_sync_pending (next_attempt_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_asaas_sync_pending_subscription
    ON asaas_sync_pending (subscription_id, created_at DESC);

CREATE INDEX idx_asaas_sync_pending_correlation
    ON asaas_sync_pending (correlation_id)
    WHERE correlation_id IS NOT NULL;

-- =============================================================
-- Row-Level Security
-- =============================================================

ALTER TABLE asaas_sync_pending ENABLE ROW LEVEL SECURITY;
ALTER TABLE asaas_sync_pending FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON asaas_sync_pending
    USING (
        company_id = current_setting('app.current_company_id', true)::bigint
        OR current_setting('app.bypass_rls', true) = 'true'
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON asaas_sync_pending TO payment_app;
GRANT USAGE, SELECT ON SEQUENCE asaas_sync_pending_id_seq TO payment_app;
GRANT ALL ON asaas_sync_pending TO payment_flyway;
