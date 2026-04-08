-- Create separate role for Flyway migrations (with BYPASSRLS)
CREATE ROLE payment_flyway WITH LOGIN PASSWORD 'dev_password' BYPASSRLS;
GRANT ALL PRIVILEGES ON DATABASE payment_db TO payment_flyway;
GRANT ALL ON SCHEMA public TO payment_flyway;

-- Ensure the application role does NOT have BYPASSRLS
-- (payment_app is created by POSTGRES_USER, already without BYPASSRLS)
ALTER ROLE payment_app SET app.current_company_id = '';
