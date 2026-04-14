package br.com.holding.payments.reconciliation;

import br.com.holding.payments.reconciliation.dto.DlqReplayResult;
import br.com.holding.payments.reconciliation.dto.ReconciliationResult;
import br.com.holding.payments.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/reconciliation")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HOLDING_ADMIN', 'COMPANY_ADMIN')")
@Tag(name = "Reconciliacao", description = "Conciliacao de dados com Asaas e replay de DLQ. " +
        "Todos os endpoints sao escopados pela empresa do usuario autenticado.")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @PostMapping("/charges")
    @Operation(summary = "Reconciliar cobrancas",
            description = "Compara status local com Asaas e corrige divergencias automaticamente quando possivel")
    public ReconciliationResult reconcileCharges(
            @RequestParam(defaultValue = "3") int daysBack) {
        Long companyId = TenantContext.getRequiredCompanyId();
        LocalDate since = LocalDate.now().minusDays(daysBack);
        return reconciliationService.reconcileChargesSince(companyId, since);
    }

    @PostMapping("/subscriptions")
    @Operation(summary = "Reconciliar assinaturas",
            description = "Compara status local de assinaturas com Asaas")
    public ReconciliationResult reconcileSubscriptions() {
        Long companyId = TenantContext.getRequiredCompanyId();
        return reconciliationService.reconcileSubscriptions(companyId);
    }

    @PostMapping("/dlq/replay")
    @Operation(summary = "Reprocessar eventos DLQ",
            description = "Marca eventos em DLQ (webhook e outbox) como PENDING para nova tentativa")
    public DlqReplayResult replayDlq() {
        Long companyId = TenantContext.getRequiredCompanyId();
        return reconciliationService.replayDLQ(companyId);
    }
}
