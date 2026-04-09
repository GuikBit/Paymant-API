package br.com.holding.payments.planchange;

import br.com.holding.payments.planchange.dto.PlanChangePreviewResponse;
import br.com.holding.payments.planchange.dto.PlanChangeResponse;
import br.com.holding.payments.planchange.dto.RequestPlanChangeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscriptions/{subscriptionId}")
@RequiredArgsConstructor
@Tag(name = "Mudanca de Plano", description = "Upgrade, downgrade e sidegrade de assinaturas")
public class PlanChangeController {

    private final PlanChangeService planChangeService;

    @PostMapping("/preview-change")
    @Operation(summary = "Preview da mudanca de plano",
            description = "Calcula o pro-rata e mostra o impacto financeiro sem efetuar a mudanca")
    public PlanChangePreviewResponse previewChange(
            @PathVariable Long subscriptionId,
            @RequestParam Long newPlanId) {
        return planChangeService.previewChange(subscriptionId, newPlanId);
    }

    @PostMapping("/change-plan")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Solicitar mudanca de plano",
            description = "Efetua a mudanca de plano conforme politica da empresa (IMMEDIATE_PRORATA, END_OF_CYCLE, IMMEDIATE_NO_PRORATA)")
    public PlanChangeResponse changePlan(
            @PathVariable Long subscriptionId,
            @Valid @RequestBody RequestPlanChangeRequest request) {
        return planChangeService.requestChange(subscriptionId, request);
    }

    @GetMapping("/plan-changes")
    @Operation(summary = "Listar historico de mudancas de plano")
    public Page<PlanChangeResponse> listChanges(
            @PathVariable Long subscriptionId,
            @PageableDefault(size = 20) Pageable pageable) {
        return planChangeService.listChanges(subscriptionId, pageable);
    }

    @DeleteMapping("/plan-changes/{changeId}")
    @Operation(summary = "Cancelar mudanca de plano pendente")
    public PlanChangeResponse cancelChange(
            @PathVariable Long subscriptionId,
            @PathVariable Long changeId) {
        return planChangeService.cancelChange(subscriptionId, changeId);
    }
}
