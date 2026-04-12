package br.com.holding.payments.apikey;

import br.com.holding.payments.apikey.dto.ApiKeyResponse;
import br.com.holding.payments.apikey.dto.CreateApiKeyRequest;
import br.com.holding.payments.apikey.dto.CreateApiKeyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HOLDING_ADMIN', 'COMPANY_ADMIN')")
@Tag(name = "API Keys", description = "Gerenciamento de API Keys para integracao sistema-a-sistema")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    @Operation(summary = "Criar nova API Key",
            description = "Gera uma nova API Key vinculada a empresa do usuario autenticado. "
                    + "A chave completa (rawKey) e retornada APENAS nesta resposta — armazene-a com seguranca. "
                    + "Apos esta chamada, somente o prefixo da chave sera visivel.")
    public ResponseEntity<CreateApiKeyResponse> create(@Valid @RequestBody CreateApiKeyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(apiKeyService.create(request));
    }

    @GetMapping
    @Operation(summary = "Listar API Keys da empresa",
            description = "Retorna as API Keys da empresa do usuario autenticado. "
                    + "A chave completa nunca e exibida — apenas o prefixo para identificacao.")
    public ResponseEntity<Page<ApiKeyResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(apiKeyService.listByCompany(pageable));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revogar API Key",
            description = "Desativa permanentemente uma API Key. "
                    + "Sistemas que utilizam esta chave perderao o acesso imediatamente.")
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        apiKeyService.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
