-- =============================================================
-- V7: Allow controlled cross-tenant access via app.bypass_rls flag
--
-- Background: scheduled jobs (e.g. WebhookProcessor) need to scan
-- tenant-scoped tables across all companies to pick up work, and
-- only AFTER selecting a row do they know which tenant context to
-- set. The previous design relied on @CrossTenant skipping the
-- TenantRlsAspect entirely, but since the application role does
-- NOT have BYPASSRLS, the policy filter (`company_id = NULL`) hid
-- every row and the jobs silently processed nothing.
--
-- This migration extends every tenant_isolation policy with an
-- explicit bypass clause: rows are visible when either the current
-- tenant matches OR the session sets app.bypass_rls = 'true'.
-- The aspect now sets that flag for @CrossTenant methods via
-- SET LOCAL, keeping the bypass scoped to the current transaction.
-- =============================================================

DROP POLICY IF EXISTS tenant_isolation ON customers;
CREATE POLICY tenant_isolation ON customers
    USING (
        company_id = current_setting('app.current_company_id', true)::bigint
        OR current_setting('app.bypass_rls', true) = 'true'
    );

DROP POLICY IF EXISTS tenant_isolation ON plans;
CREATE POLICY tenant_isolation ON plans
    USING (
        company_id = current_setting('app.current_company_id', true)::bigint
        OR current_setting('app.bypass_rls', true) = 'true'
    );

DROP POLICY IF EXISTS tenant_isolation ON subscriptions;
CREATE POLICY tenant_isolation ON subscriptions
    USING (
        company_id = current_setting('app.current_company_id', true)::bigint
        OR current_setting('app.bypass_rls', true) = 'true'
    );

DROP POLICY IF EXISTS tenant_isolation ON installments;
CREATE POLICY tenant_isolation ON installments
    USING (
        company_id = current_setting('app.current_company_id', true)::bigint
        OR current_setting('app.bypass_rls', true) = 'true'
    );

DROP POLICY IF EXISTS tenant_isolation ON charges;
CREATE POLICY tenant_isolation ON charges
    USING (
        company_id = current_setting('app.current_company_id', true)::bigint
        OR current_setting('app.bypass_rls', true) = 'true'
    );

DROP POLICY IF EXISTS tenant_isolation ON webhook_events;
CREATE POLICY tenant_isolation ON webhook_events
    USING (
        company_id = current_setting('app.current_company_id', true)::bigint
        OR current_setting('app.bypass_rls', true) = 'true'
    );

DROP POLICY IF EXISTS tenant_isolation ON idempotency_keys;
CREATE POLICY tenant_isolation ON idempotency_keys
    USING (
        company_id = current_setting('app.current_company_id', true)::bigint
        OR current_setting('app.bypass_rls', true) = 'true'
    );

DROP POLICY IF EXISTS tenant_isolation ON outbox;
CREATE POLICY tenant_isolation ON outbox
    USING (
        company_id = current_setting('app.current_company_id', true)::bigint
        OR current_setting('app.bypass_rls', true) = 'true'
    );

DROP POLICY IF EXISTS tenant_isolation ON customer_credit_ledger;
CREATE POLICY tenant_isolation ON customer_credit_ledger
    USING (
        company_id = current_setting('app.current_company_id', true)::bigint
        OR current_setting('app.bypass_rls', true) = 'true'
    );

DROP POLICY IF EXISTS tenant_isolation ON subscription_plan_changes;
CREATE POLICY tenant_isolation ON subscription_plan_changes
    USING (
        company_id = current_setting('app.current_company_id', true)::bigint
        OR current_setting('app.bypass_rls', true) = 'true'
    );

DROP POLICY IF EXISTS tenant_isolation ON audit_log;
CREATE POLICY tenant_isolation ON audit_log
    USING (
        company_id = current_setting('app.current_company_id', true)::bigint
        OR current_setting('app.bypass_rls', true) = 'true'
    );
