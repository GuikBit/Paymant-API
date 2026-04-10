-- =============================================================
-- V4: Sistema de Cupons de Desconto
-- Adiciona tabelas de cupons e utilizacoes, campos de cupom
-- em subscriptions e charges, e policies de RLS.
-- =============================================================

-- 1. Tabela de cupons
CREATE TABLE coupons (
    id                      BIGSERIAL       PRIMARY KEY,
    company_id              BIGINT          NOT NULL REFERENCES companies(id),
    code                    VARCHAR(50)     NOT NULL,
    description             VARCHAR(255),
    discount_type           VARCHAR(20)     NOT NULL,
    discount_value          NUMERIC(12,2)   NOT NULL,
    scope                   VARCHAR(20)     NOT NULL,
    application_type        VARCHAR(20)     NOT NULL DEFAULT 'FIRST_CHARGE',
    recurrence_months       INT,
    valid_from              TIMESTAMP,
    valid_until             TIMESTAMP,
    max_uses                INT,
    max_uses_per_customer   INT,
    usage_count             INT             NOT NULL DEFAULT 0,
    allowed_plans           JSONB,
    allowed_customers       JSONB,
    allowed_cycle           VARCHAR(20),
    active                  BOOLEAN         NOT NULL DEFAULT true,
    deleted_at              TIMESTAMP,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_coupons_company_code ON coupons(company_id, code) WHERE deleted_at IS NULL;
CREATE INDEX idx_coupons_company ON coupons(company_id);
CREATE INDEX idx_coupons_active ON coupons(company_id, active) WHERE deleted_at IS NULL;

-- 2. Tabela de utilizacoes (append-only)
CREATE TABLE coupon_usages (
    id                BIGSERIAL       PRIMARY KEY,
    company_id        BIGINT          NOT NULL REFERENCES companies(id),
    coupon_id         BIGINT          NOT NULL REFERENCES coupons(id),
    coupon_code       VARCHAR(50),
    customer_id       BIGINT          NOT NULL REFERENCES customers(id),
    subscription_id   BIGINT,
    charge_id         BIGINT,
    original_value    NUMERIC(12,2)   NOT NULL,
    discount_amount   NUMERIC(12,2)   NOT NULL,
    final_value       NUMERIC(12,2)   NOT NULL,
    plan_code         VARCHAR(50),
    cycle             VARCHAR(20),
    used_at           TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_coupon_usages_coupon ON coupon_usages(coupon_id);
CREATE INDEX idx_coupon_usages_customer ON coupon_usages(customer_id);
CREATE INDEX idx_coupon_usages_company ON coupon_usages(company_id);

-- 3. Campos de cupom na subscription
ALTER TABLE subscriptions ADD COLUMN coupon_id BIGINT REFERENCES coupons(id);
ALTER TABLE subscriptions ADD COLUMN coupon_code VARCHAR(50);
ALTER TABLE subscriptions ADD COLUMN coupon_discount_amount NUMERIC(12,2);
ALTER TABLE subscriptions ADD COLUMN coupon_uses_remaining INT;

-- 4. Campos de cupom na charge
ALTER TABLE charges ADD COLUMN coupon_id BIGINT REFERENCES coupons(id);
ALTER TABLE charges ADD COLUMN coupon_code VARCHAR(50);
ALTER TABLE charges ADD COLUMN discount_amount NUMERIC(12,2);
ALTER TABLE charges ADD COLUMN original_value NUMERIC(12,2);

-- 5. RLS policies
ALTER TABLE coupons ENABLE ROW LEVEL SECURITY;
ALTER TABLE coupons FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON coupons
    USING (company_id = current_setting('app.current_company_id')::bigint);

ALTER TABLE coupon_usages ENABLE ROW LEVEL SECURITY;
ALTER TABLE coupon_usages FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON coupon_usages
    USING (company_id = current_setting('app.current_company_id')::bigint);
