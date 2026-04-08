-- =============================================================
-- V2: Enable Row-Level Security (RLS) on all tenant-scoped tables
-- =============================================================

-- Enable RLS on each transactional table
ALTER TABLE customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE customers FORCE ROW LEVEL SECURITY;

ALTER TABLE plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE plans FORCE ROW LEVEL SECURITY;

ALTER TABLE subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscriptions FORCE ROW LEVEL SECURITY;

ALTER TABLE installments ENABLE ROW LEVEL SECURITY;
ALTER TABLE installments FORCE ROW LEVEL SECURITY;

ALTER TABLE charges ENABLE ROW LEVEL SECURITY;
ALTER TABLE charges FORCE ROW LEVEL SECURITY;

ALTER TABLE webhook_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE webhook_events FORCE ROW LEVEL SECURITY;

ALTER TABLE idempotency_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_keys FORCE ROW LEVEL SECURITY;

ALTER TABLE outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox FORCE ROW LEVEL SECURITY;

ALTER TABLE customer_credit_ledger ENABLE ROW LEVEL SECURITY;
ALTER TABLE customer_credit_ledger FORCE ROW LEVEL SECURITY;

ALTER TABLE subscription_plan_changes ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription_plan_changes FORCE ROW LEVEL SECURITY;

ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;

-- NOTE: users table does NOT have RLS.
-- Login requires cross-tenant lookup by email (before company_id is known).
-- User isolation is enforced at the application level via Spring Security.

-- =============================================================
-- RLS Policies: isolate rows by app.current_company_id session variable
-- =============================================================

CREATE POLICY tenant_isolation ON customers
    USING (company_id = current_setting('app.current_company_id', true)::bigint);

CREATE POLICY tenant_isolation ON plans
    USING (company_id = current_setting('app.current_company_id', true)::bigint);

CREATE POLICY tenant_isolation ON subscriptions
    USING (company_id = current_setting('app.current_company_id', true)::bigint);

CREATE POLICY tenant_isolation ON installments
    USING (company_id = current_setting('app.current_company_id', true)::bigint);

CREATE POLICY tenant_isolation ON charges
    USING (company_id = current_setting('app.current_company_id', true)::bigint);

CREATE POLICY tenant_isolation ON webhook_events
    USING (company_id = current_setting('app.current_company_id', true)::bigint);

CREATE POLICY tenant_isolation ON idempotency_keys
    USING (company_id = current_setting('app.current_company_id', true)::bigint);

CREATE POLICY tenant_isolation ON outbox
    USING (company_id = current_setting('app.current_company_id', true)::bigint);

CREATE POLICY tenant_isolation ON customer_credit_ledger
    USING (company_id = current_setting('app.current_company_id', true)::bigint);

CREATE POLICY tenant_isolation ON subscription_plan_changes
    USING (company_id = current_setting('app.current_company_id', true)::bigint);

CREATE POLICY tenant_isolation ON audit_log
    USING (company_id = current_setting('app.current_company_id', true)::bigint);

-- No RLS policy on users — see note above.
