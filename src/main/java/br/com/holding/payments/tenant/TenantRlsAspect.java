package br.com.holding.payments.tenant;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Aspect that sets the PostgreSQL session variable `app.current_company_id`
 * at the beginning of every @Transactional method, enabling Row-Level Security.
 *
 * Runs INSIDE the transaction (Order = Ordered.LOWEST_PRECEDENCE - 1)
 * so that SET LOCAL is scoped to the active transaction.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class TenantRlsAspect {

    private final EntityManager entityManager;

    @Around("@annotation(org.springframework.transaction.annotation.Transactional) || " +
            "@within(org.springframework.transaction.annotation.Transactional)")
    public Object setTenantContext(ProceedingJoinPoint joinPoint) throws Throwable {
        if (isCrossTenant(joinPoint)) {
            // RLS policies allow visibility when app.bypass_rls = 'true' (see V7).
            // SET LOCAL keeps the bypass scoped to the current transaction.
            entityManager.createNativeQuery("SET LOCAL app.bypass_rls = 'true'")
                    .executeUpdate();
            return joinPoint.proceed();
        }

        Long companyId = TenantContext.getCompanyId();

        if (companyId != null) {
            entityManager.createNativeQuery("SET LOCAL app.current_company_id = '" + companyId + "'")
                    .executeUpdate();
        } else {
            log.warn("No company_id in TenantContext for method {}#{}. RLS will filter all rows.",
                    joinPoint.getTarget().getClass().getSimpleName(),
                    joinPoint.getSignature().getName());
        }

        return joinPoint.proceed();
    }

    private boolean isCrossTenant(ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Class<?> targetClass = joinPoint.getTarget() != null
                    ? joinPoint.getTarget().getClass()
                    : signature.getDeclaringType();
            Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), targetClass);
            return method.isAnnotationPresent(CrossTenant.class);
        } catch (Exception e) {
            return false;
        }
    }
}
