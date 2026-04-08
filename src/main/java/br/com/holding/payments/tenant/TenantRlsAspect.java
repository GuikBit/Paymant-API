package br.com.holding.payments.tenant;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Aspect that sets the PostgreSQL session variable `app.current_company_id`
 * at the beginning of every @Transactional method, enabling Row-Level Security.
 *
 * Runs BEFORE the transaction opens (Order = Ordered.HIGHEST_PRECEDENCE + 1)
 * so that the SET LOCAL is scoped to the transaction.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TenantRlsAspect {

    private final EntityManager entityManager;

    @Around("@annotation(org.springframework.transaction.annotation.Transactional) || " +
            "@within(org.springframework.transaction.annotation.Transactional)")
    public Object setTenantContext(ProceedingJoinPoint joinPoint) throws Throwable {
        Long companyId = TenantContext.getCompanyId();

        if (companyId != null) {
            entityManager.createNativeQuery("SET LOCAL app.current_company_id = :companyId")
                    .setParameter("companyId", companyId.toString())
                    .executeUpdate();
        } else {
            log.warn("No company_id in TenantContext for method {}#{}. RLS will filter all rows.",
                    joinPoint.getTarget().getClass().getSimpleName(),
                    joinPoint.getSignature().getName());
        }

        return joinPoint.proceed();
    }
}
