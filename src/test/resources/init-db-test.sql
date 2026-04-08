-- Test database init: ensure app role does not have BYPASSRLS
ALTER ROLE payment_app SET app.current_company_id = '';
