package br.com.holding.payments.audit;

import br.com.holding.payments.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actor, String action, String entity, String entityId,
                       Map<String, Object> before, Map<String, Object> after) {
        Long companyId = TenantContext.getCompanyId();

        AuditLog auditLog = AuditLog.builder()
                .companyId(companyId)
                .actor(actor)
                .action(action)
                .entity(entity)
                .entityId(entityId)
                .beforeState(before)
                .afterState(after)
                .build();

        auditLogRepository.save(auditLog);
        log.debug("Audit: {} {} {} {} by {}", action, entity, entityId,
                companyId != null ? "(company=" + companyId + ")" : "", actor);
    }
}
