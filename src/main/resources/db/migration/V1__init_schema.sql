-- =============================================================
-- V1: Schema base - Payment API Multi-Tenant
-- =============================================================

-- Companies (Tenants)
CREATE TABLE companies (
    id              BIGSERIAL       PRIMARY KEY,
    cnpj            VARCHAR(18)     NOT NULL,
    razao_social    VARCHAR(255)    NOT NULL,
    nome_fantasia   VARCHAR(255),
    email           VARCHAR(255),
    phone           VARCHAR(20),
    asaas_api_key_encrypted TEXT,
    asaas_env       VARCHAR(20)     NOT NULL DEFAULT 'SANDBOX',
    webhook_token   VARCHAR(255),
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    plan_change_policy VARCHAR(30)  NOT NULL DEFAULT 'IMMEDIATE_PRORATA',
    downgrade_validation_strategy VARCHAR(20) NOT NULL DEFAULT 'BLOCK',
    grace_period_days INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_company_cnpj UNIQUE (cnpj)
);

-- Users
CREATE TABLE users (
    id              BIGSERIAL       PRIMARY KEY,
    company_id      BIGINT          NOT NULL REFERENCES companies(id),
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    roles           VARCHAR(255)    NOT NULL DEFAULT 'ROLE_COMPANY_OPERATOR',
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_email UNIQUE (email)
);

-- Customers (Clients - scoped by company)
CREATE TABLE customers (
    id              BIGSERIAL       PRIMARY KEY,
    company_id      BIGINT          NOT NULL REFERENCES companies(id),
    asaas_id        VARCHAR(100),
    name            VARCHAR(255)    NOT NULL,
    document        VARCHAR(18)     NOT NULL,
    email           VARCHAR(255),
    phone           VARCHAR(20),
    address_street  VARCHAR(255),
    address_number  VARCHAR(20),
    address_complement VARCHAR(100),
    address_neighborhood VARCHAR(100),
    address_city    VARCHAR(100),
    address_state   VARCHAR(2),
    address_postal_code VARCHAR(10),
    credit_balance  NUMERIC(12,2)   NOT NULL DEFAULT 0.00,
    deleted_at      TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Partial unique index: allows re-registration after soft delete
CREATE UNIQUE INDEX uq_customer_doc_active
    ON customers (company_id, document)
    WHERE deleted_at IS NULL;

-- Plans
CREATE TABLE plans (
    id              BIGSERIAL       PRIMARY KEY,
    company_id      BIGINT          NOT NULL REFERENCES companies(id),
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    value           NUMERIC(12,2)   NOT NULL,
    cycle           VARCHAR(20)     NOT NULL,
    trial_days      INT             NOT NULL DEFAULT 0,
    setup_fee       NUMERIC(12,2)   NOT NULL DEFAULT 0.00,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    version         INT             NOT NULL DEFAULT 1,
    limits          JSONB,
    features        JSONB,
    tier_order      INT             NOT NULL DEFAULT 0,
    deleted_at      TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Subscriptions
CREATE TABLE subscriptions (
    id                      BIGSERIAL       PRIMARY KEY,
    company_id              BIGINT          NOT NULL REFERENCES companies(id),
    customer_id             BIGINT          NOT NULL REFERENCES customers(id),
    plan_id                 BIGINT          NOT NULL REFERENCES plans(id),
    asaas_id                VARCHAR(100),
    billing_type            VARCHAR(20)     NOT NULL,
    current_period_start    TIMESTAMP,
    current_period_end      TIMESTAMP,
    next_due_date           DATE,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Installments
CREATE TABLE installments (
    id                  BIGSERIAL       PRIMARY KEY,
    company_id          BIGINT          NOT NULL REFERENCES companies(id),
    customer_id         BIGINT          NOT NULL REFERENCES customers(id),
    asaas_id            VARCHAR(100),
    total_value         NUMERIC(12,2)   NOT NULL,
    installment_count   INT             NOT NULL,
    billing_type        VARCHAR(20)     NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Charges
CREATE TABLE charges (
    id                  BIGSERIAL       PRIMARY KEY,
    company_id          BIGINT          NOT NULL REFERENCES companies(id),
    customer_id         BIGINT          NOT NULL REFERENCES customers(id),
    subscription_id     BIGINT          REFERENCES subscriptions(id),
    installment_id      BIGINT          REFERENCES installments(id),
    asaas_id            VARCHAR(100),
    billing_type        VARCHAR(20)     NOT NULL,
    value               NUMERIC(12,2)   NOT NULL,
    due_date            DATE            NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    origin              VARCHAR(20)     NOT NULL DEFAULT 'API',
    external_reference  VARCHAR(100),
    pix_qrcode          TEXT,
    pix_copy_paste      TEXT,
    boleto_url          TEXT,
    invoice_url         TEXT,
    installment_number  INT,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Webhook Events
CREATE TABLE webhook_events (
    id              BIGSERIAL       PRIMARY KEY,
    company_id      BIGINT          NOT NULL REFERENCES companies(id),
    asaas_event_id  VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         JSONB           NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    attempt_count   INT             NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP,
    processed_at    TIMESTAMP,
    last_error      TEXT,
    received_at     TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_webhook_event UNIQUE (asaas_event_id, company_id)
);

-- Idempotency Keys
CREATE TABLE idempotency_keys (
    id              BIGSERIAL       PRIMARY KEY,
    company_id      BIGINT          NOT NULL REFERENCES companies(id),
    key             VARCHAR(100)    NOT NULL,
    endpoint        VARCHAR(100)    NOT NULL,
    request_hash    VARCHAR(64),
    response_status INT,
    response_body   JSONB,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP       NOT NULL,
    CONSTRAINT uq_idempotency UNIQUE (company_id, endpoint, key)
);

-- Outbox (Domain Events)
CREATE TABLE outbox (
    id              BIGSERIAL       PRIMARY KEY,
    company_id      BIGINT          NOT NULL REFERENCES companies(id),
    aggregate_type  VARCHAR(100)    NOT NULL,
    aggregate_id    VARCHAR(100)    NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         JSONB           NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    attempt_count   INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP,
    last_error      TEXT
);

-- Customer Credit Ledger
CREATE TABLE customer_credit_ledger (
    id              BIGSERIAL       PRIMARY KEY,
    company_id      BIGINT          NOT NULL REFERENCES companies(id),
    customer_id     BIGINT          NOT NULL REFERENCES customers(id),
    type            VARCHAR(10)     NOT NULL,
    origin          VARCHAR(30)     NOT NULL,
    reference_id    VARCHAR(100),
    amount          NUMERIC(12,2)   NOT NULL,
    balance_after   NUMERIC(12,2)   NOT NULL,
    description     TEXT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Subscription Plan Changes
CREATE TABLE subscription_plan_changes (
    id                  BIGSERIAL       PRIMARY KEY,
    company_id          BIGINT          NOT NULL REFERENCES companies(id),
    subscription_id     BIGINT          NOT NULL REFERENCES subscriptions(id),
    previous_plan_id    BIGINT          NOT NULL REFERENCES plans(id),
    requested_plan_id   BIGINT          NOT NULL REFERENCES plans(id),
    change_type         VARCHAR(20)     NOT NULL,
    policy              VARCHAR(30)     NOT NULL,
    delta_amount        NUMERIC(12,2)   NOT NULL DEFAULT 0.00,
    proration_credit    NUMERIC(12,2)   NOT NULL DEFAULT 0.00,
    proration_charge    NUMERIC(12,2)   NOT NULL DEFAULT 0.00,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    charge_id           BIGINT          REFERENCES charges(id),
    credit_ledger_id    BIGINT          REFERENCES customer_credit_ledger(id),
    scheduled_for       TIMESTAMP,
    effective_at        TIMESTAMP,
    requested_by        VARCHAR(100),
    requested_at        TIMESTAMP       NOT NULL DEFAULT NOW(),
    failure_reason      TEXT
);

-- Audit Log
CREATE TABLE audit_log (
    id              BIGSERIAL       PRIMARY KEY,
    company_id      BIGINT          REFERENCES companies(id),
    actor           VARCHAR(255)    NOT NULL,
    action          VARCHAR(100)    NOT NULL,
    entity          VARCHAR(100)    NOT NULL,
    entity_id       VARCHAR(100),
    before_state    JSONB,
    after_state     JSONB,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- =============================================================
-- Indexes for performance
-- =============================================================
CREATE INDEX idx_customers_company ON customers(company_id);
CREATE INDEX idx_customers_asaas_id ON customers(asaas_id);
CREATE INDEX idx_plans_company ON plans(company_id);
CREATE INDEX idx_subscriptions_company ON subscriptions(company_id);
CREATE INDEX idx_subscriptions_customer ON subscriptions(customer_id);
CREATE INDEX idx_subscriptions_asaas_id ON subscriptions(asaas_id);
CREATE INDEX idx_charges_company ON charges(company_id);
CREATE INDEX idx_charges_customer ON charges(customer_id);
CREATE INDEX idx_charges_subscription ON charges(subscription_id);
CREATE INDEX idx_charges_asaas_id ON charges(asaas_id);
CREATE INDEX idx_charges_status ON charges(company_id, status);
CREATE INDEX idx_charges_due_date ON charges(company_id, due_date);
CREATE INDEX idx_charges_origin ON charges(company_id, origin);
CREATE INDEX idx_webhook_events_status ON webhook_events(status, next_attempt_at);
CREATE INDEX idx_webhook_events_company ON webhook_events(company_id);
CREATE INDEX idx_idempotency_expires ON idempotency_keys(expires_at);
CREATE INDEX idx_outbox_status ON outbox(status, created_at);
CREATE INDEX idx_audit_log_entity ON audit_log(entity, entity_id);
CREATE INDEX idx_audit_log_company ON audit_log(company_id, created_at);
CREATE INDEX idx_credit_ledger_customer ON customer_credit_ledger(company_id, customer_id);
