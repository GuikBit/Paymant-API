-- =============================================
-- V5: API Keys para integracao sistema-a-sistema
-- =============================================

CREATE TABLE api_keys (
    id              BIGSERIAL       PRIMARY KEY,
    company_id      BIGINT          NOT NULL REFERENCES companies(id),
    key_prefix      VARCHAR(8)      NOT NULL,
    key_hash        VARCHAR(128)    NOT NULL UNIQUE,
    name            VARCHAR(100)    NOT NULL,
    description     VARCHAR(500),
    roles           VARCHAR(255)    NOT NULL DEFAULT 'ROLE_COMPANY_OPERATOR',
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    last_used_at    TIMESTAMP,
    expires_at      TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_keys_key_hash ON api_keys (key_hash);
CREATE INDEX idx_api_keys_company_id ON api_keys (company_id);
CREATE INDEX idx_api_keys_active ON api_keys (active) WHERE active = TRUE;

-- RLS: autenticacao precisa de SELECT sem tenant (lookup por hash unico)
-- CRUD normal e filtrado pelo tenant logado
ALTER TABLE api_keys ENABLE ROW LEVEL SECURITY;

-- SELECT: permite leitura sem tenant (para autenticacao) e com tenant (para listagem)
CREATE POLICY api_keys_select ON api_keys
    FOR SELECT
    USING (TRUE);

-- INSERT/UPDATE/DELETE: restrito ao tenant
CREATE POLICY api_keys_modify ON api_keys
    FOR ALL
    USING (company_id = CAST(current_setting('app.current_company_id', true) AS BIGINT))
    WITH CHECK (company_id = CAST(current_setting('app.current_company_id', true) AS BIGINT));

GRANT SELECT, INSERT, UPDATE, DELETE ON api_keys TO payment_app;
GRANT USAGE, SELECT ON SEQUENCE api_keys_id_seq TO payment_app;
GRANT ALL ON api_keys TO payment_flyway;
