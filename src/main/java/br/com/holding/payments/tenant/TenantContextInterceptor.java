package br.com.holding.payments.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantContextInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantContextInterceptor.class);
    private static final String COMPANY_HEADER = "X-Company-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String companyIdHeader = request.getHeader(COMPANY_HEADER);
        if (companyIdHeader != null) {
            try {
                TenantContext.setCompanyId(Long.parseLong(companyIdHeader));
            } catch (NumberFormatException e) {
                log.warn("Invalid X-Company-Id header value: {}", companyIdHeader);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return false;
            }
        }
        // TODO: In Fase 1, also extract company_id from JWT claims
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }
}
