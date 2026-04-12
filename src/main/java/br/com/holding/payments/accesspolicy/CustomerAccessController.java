package br.com.holding.payments.accesspolicy;

import br.com.holding.payments.accesspolicy.dto.AccessStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Validacao de Acesso", description = "Verificacao de status financeiro de clientes para sistemas integrados")
public class CustomerAccessController {

    private final CustomerAccessService customerAccessService;

    @GetMapping("/{id}/access-status")
    @Operation(summary = "Verificar status de acesso do cliente",
            description = "Verifica se o cliente esta liberado ou bloqueado com base nas regras de politica de acesso "
                    + "configuradas pela empresa. Retorna os motivos do bloqueio e um resumo financeiro. "
                    + "Pode ser chamado via API Key (X-API-Key) ou JWT. "
                    + "O resultado e cacheado no Redis e um evento e publicado no outbox quando o status muda.")
    public ResponseEntity<AccessStatusResponse> checkAccess(@PathVariable Long id) {
        return ResponseEntity.ok(customerAccessService.checkAccess(id));
    }
}
