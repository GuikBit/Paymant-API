package br.com.holding.payments.plan;

import br.com.holding.payments.plan.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
@Tag(name = "Planos", description = "Gerenciamento de planos de assinatura por empresa. " +
        "Suporta versionamento (mudanca de preco sem afetar assinaturas existentes), " +
        "soft delete e configuracao de limites e features via JSONB.")
public class PlanController {

    private final PlanService planService;

    @PostMapping
    @Operation(summary = "Criar novo plano",
            description = "Cria um novo plano de assinatura para a empresa do usuario autenticado. " +
                    "Os campos 'limits' e 'features' aceitam JSON livre para definir limites de uso " +
                    "(ex: {\"users\": 10, \"messages\": 1000}) e funcionalidades exclusivas (ex: [\"feature_a\", \"feature_b\"]).")
    public ResponseEntity<PlanResponse> create(@Valid @RequestBody CreatePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.create(request));
    }

    @GetMapping
    @Operation(summary = "Listar planos",
            description = "Retorna lista paginada de planos ativos (nao deletados) da empresa. " +
                    "Suporta ordenacao por valor, nome ou tier_order. Exemplo: ?sort=tierOrder,asc")
    public ResponseEntity<Page<PlanResponse>> findAll(Pageable pageable) {
        return ResponseEntity.ok(planService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar plano por ID",
            description = "Retorna os dados completos de um plano. Retorna 404 se o plano nao existir ou estiver deletado.")
    public ResponseEntity<PlanResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(planService.findById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar plano",
            description = "Atualiza os dados do plano. Apenas os campos enviados serao alterados. " +
                    "Para mudancas de preco que nao devem afetar assinaturas existentes, " +
                    "use o endpoint POST /{id}/new-version.")
    public ResponseEntity<PlanResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody UpdatePlanRequest request) {
        return ResponseEntity.ok(planService.update(id, request));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Ativar plano",
            description = "Reativa um plano que foi desativado. Planos ativos ficam disponiveis para novas assinaturas.")
    public ResponseEntity<PlanResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(planService.activate(id));
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Desativar plano",
            description = "Desativa o plano para que nao aceite novas assinaturas. " +
                    "Assinaturas existentes continuam funcionando normalmente com o plano desativado.")
    public ResponseEntity<PlanResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(planService.deactivate(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir plano (soft delete)",
            description = "Realiza exclusao logica do plano (soft delete). O registro e mantido no banco " +
                    "para preservar integridade de assinaturas historicas. " +
                    "Bloqueado se houver assinaturas ativas vinculadas ao plano (implementado na Fase 7).")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        planService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/new-version")
    @Operation(summary = "Criar nova versao do plano",
            description = "Cria uma nova versao do plano com preco ou configuracoes alteradas. " +
                    "O plano original e desativado automaticamente. Assinaturas existentes continuam " +
                    "vinculadas a versao antiga. Novas assinaturas usam a nova versao.")
    public ResponseEntity<PlanResponse> newVersion(@PathVariable Long id,
                                                    @Valid @RequestBody UpdatePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.cloneForNewVersion(id, request));
    }
}
