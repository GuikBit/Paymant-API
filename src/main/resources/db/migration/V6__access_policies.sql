-- =============================================
-- V6: Politicas de acesso (bloqueio financeiro)
-- =============================================

CREATE TABLE access_policies (
    id                              BIGSERIAL       PRIMARY KEY,
    company_id                      BIGINT          NOT NULL UNIQUE REFERENCES companies(id),

    -- Quantidade minima de cobrancas vencidas para bloquear
    max_overdue_charges             INT             NOT NULL DEFAULT 1,

    -- Dias de tolerancia apos vencimento antes de considerar como pendencia
    overdue_tolerance_days          INT             NOT NULL DEFAULT 0,

    -- Bloquear se o cliente tem assinatura SUSPENDED
    block_on_suspended_subscription BOOLEAN         NOT NULL DEFAULT TRUE,

    -- Bloquear por cobrancas avulsas (nao vinculadas a assinatura)
    block_on_standalone_charges     BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Bloquear por cobrancas de assinatura
    block_on_subscription_charges   BOOLEAN         NOT NULL DEFAULT TRUE,

    -- Bloquear se o saldo de credito esta negativo
    block_on_negative_credit        BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Mensagem personalizada exibida ao cliente bloqueado
    custom_block_message            TEXT,

    -- TTL do cache em minutos
    cache_ttl_minutes               INT             NOT NULL DEFAULT 5,

    created_at                      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- RLS
ALTER TABLE access_policies ENABLE ROW LEVEL SECURITY;

CREATE POLICY access_policies_tenant_isolation ON access_policies
    USING (company_id = CAST(current_setting('app.current_company_id', true) AS BIGINT));

GRANT SELECT, INSERT, UPDATE, DELETE ON access_policies TO payment_app;
GRANT USAGE, SELECT ON SEQUENCE access_policies_id_seq TO payment_app;
GRANT ALL ON access_policies TO payment_flyway;
