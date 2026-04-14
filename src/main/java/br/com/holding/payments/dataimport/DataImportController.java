package br.com.holding.payments.dataimport;

import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.dataimport.dto.ImportResult;
import br.com.holding.payments.dataimport.dto.WipeResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/import")
@RequiredArgsConstructor
@Tag(name = "Data Import", description = "Importacao e limpeza de dados do Asaas para o sistema local")
public class DataImportController {

    private final AsaasDataImportService importService;
    private final OrgDataWipeService wipeService;

    @PostMapping("/asaas")
    @PreAuthorize("hasAnyRole('HOLDING_ADMIN', 'COMPANY_ADMIN')")
    @Operation(
            summary = "Importar todos os dados do Asaas",
            description = "Busca clientes, assinaturas, cobrancas e parcelamentos do Asaas e popula o sistema local. "
                    + "Registros ja existentes (pelo asaas_id) sao ignorados. Requer empresa cadastrada com chave de API do Asaas configurada."
    )
    public ResponseEntity<ImportResult> importFromAsaas() {
        ImportResult result = importService.importAllFromAsaas();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/wipe")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(
            summary = "Limpar TODOS os dados da empresa (apenas SANDBOX)",
            description = "Remove clientes, assinaturas, cobrancas, planos, webhooks, outbox e demais dados "
                    + "transacionais da empresa autenticada. Util para testes contra o sandbox do Asaas. "
                    + "Para confirmar, envie o header X-Confirm-Wipe com o valor 'I-UNDERSTAND-THIS-DELETES-EVERYTHING'. "
                    + "So funciona quando a empresa esta configurada com ambiente Asaas SANDBOX."
    )
    public ResponseEntity<WipeResult> wipeOrganizationData(
            @RequestHeader(value = "X-Confirm-Wipe", required = false) String confirmation) {
        if (!"I-UNDERSTAND-THIS-DELETES-EVERYTHING".equals(confirmation)) {
            throw new BusinessException(
                    "Operacao destrutiva: envie o header X-Confirm-Wipe com o valor exato "
                            + "'I-UNDERSTAND-THIS-DELETES-EVERYTHING' para confirmar.");
        }
        return ResponseEntity.ok(wipeService.wipeAll());
    }
}
