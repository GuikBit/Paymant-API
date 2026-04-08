package br.com.holding.payments.tenant;

public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_COMPANY = new ThreadLocal<>();

    private TenantContext() {}

    public static void setCompanyId(Long companyId) {
        CURRENT_COMPANY.set(companyId);
    }

    public static Long getCompanyId() {
        return CURRENT_COMPANY.get();
    }

    public static Long getRequiredCompanyId() {
        Long companyId = CURRENT_COMPANY.get();
        if (companyId == null) {
            throw new IllegalStateException("company_id not set in TenantContext");
        }
        return companyId;
    }

    public static void clear() {
        CURRENT_COMPANY.remove();
    }
}
