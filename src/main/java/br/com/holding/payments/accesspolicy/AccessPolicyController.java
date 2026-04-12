package br.com.holding.payments.accesspolicy;

import br.com.holding.payments.accesspolicy.dto.AccessPolicyResponse;
import br.com.holding.payments.accesspolicy.dto.UpdateAccessPolicyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/access-policy")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HOLDING_ADMIN', 'COMPANY_ADMIN')")
@Tag(name = "Politica de Acesso", description = "Configuracoes de bloqueio financeiro para validacao de acesso de clientes em sistemas integrados")
public class AccessPolicyController {

    private final AccessPolicyService accessPolicyService;

    @GetMapping
    @Operation(summary = "Consultar politica de acesso",
            description = "Retorna as configuracoes atuais de bloqueio financeiro da empresa. "
                    + "Se nenhuma politica foi configurada, retorna os valores padrao.")
    public ResponseEntity<AccessPolicyResponse> getPolicy() {
        return ResponseEntity.ok(accessPolicyService.getPolicy());
    }

    @PutMapping
    @Operation(summary = "Atualizar politica de acesso",
            description = "Atualiza as configuracoes de bloqueio financeiro da empresa. "
                    + "Apenas os campos enviados serao atualizados (atualizacao parcial).")
    public ResponseEntity<AccessPolicyResponse> updatePolicy(@Valid @RequestBody UpdateAccessPolicyRequest request) {
        return ResponseEntity.ok(accessPolicyService.updatePolicy(request));
    }
}
