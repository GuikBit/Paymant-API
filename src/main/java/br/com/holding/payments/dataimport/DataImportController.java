package br.com.holding.payments.dataimport;

import br.com.holding.payments.dataimport.dto.ImportResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/import")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HOLDING_ADMIN', 'COMPANY_ADMIN')")
@Tag(name = "Data Import", description = "Importacao de dados do Asaas para o sistema local")
public class DataImportController {

    private final AsaasDataImportService importService;

    @PostMapping("/asaas")
    @Operation(
            summary = "Importar todos os dados do Asaas",
            description = "Busca clientes, assinaturas, cobrancas e parcelamentos do Asaas e popula o sistema local. "
                    + "Registros ja existentes (pelo asaas_id) sao ignorados. Requer empresa cadastrada com chave de API do Asaas configurada."
    )
    public ResponseEntity<ImportResult> importFromAsaas() {
        ImportResult result = importService.importAllFromAsaas();
        return ResponseEntity.ok(result);
    }
}
