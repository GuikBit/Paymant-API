-- Create application role (WITHOUT superuser and WITHOUT BYPASSRLS)
CREATE ROLE payment_app WITH LOGIN PASSWORD 'dev_password' NOSUPERUSER NOBYPASSRLS;
GRANT ALL PRIVILEGES ON DATABASE payment_db TO payment_app;
GRANT ALL ON SCHEMA public TO payment_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO payment_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO payment_app;

-- Create separate role for Flyway migrations (WITH BYPASSRLS)
CREATE ROLE payment_flyway WITH LOGIN PASSWORD 'dev_password' BYPASSRLS;
GRANT ALL PRIVILEGES ON DATABASE payment_db TO payment_flyway;
GRANT ALL ON SCHEMA public TO payment_flyway;

-- Set default company_id to 0 (no match) for application role
-- Using '0' instead of '' to avoid bigint cast errors in RLS policies
ALTER ROLE payment_app SET app.current_company_id = '0';
