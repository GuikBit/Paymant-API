package br.com.holding.payments.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditService auditService;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            String actor = resolveActor();
            String entityId = resolveEntityId(joinPoint, result);

            auditService.record(
                    actor,
                    auditable.action(),
                    auditable.entity(),
                    entityId,
                    null,
                    result != null ? Map.of("result", result.toString()) : null
            );
        } catch (Exception e) {
            log.warn("Failed to record audit log for {}: {}", auditable.action(), e.getMessage());
        }

        return result;
    }

    private String resolveActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getPrincipal().toString();
        }
        return "system";
    }

    private String resolveEntityId(ProceedingJoinPoint joinPoint, Object result) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        // Look for an 'id' parameter
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                if ("id".equals(paramNames[i]) && args[i] != null) {
                    return args[i].toString();
                }
            }
        }

        return null;
    }
}
