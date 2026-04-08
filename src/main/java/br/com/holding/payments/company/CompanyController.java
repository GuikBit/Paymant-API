package br.com.holding.payments.company;

import br.com.holding.payments.company.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HOLDING_ADMIN')")
@Tag(name = "Empresas", description = "Gerenciamento de empresas (tenants) do sistema. Apenas administradores da holding tem acesso a estes endpoints.")
public class CompanyController {

    private final CompanyService companyService;

    @PostMapping
    @Operation(summary = "Cadastrar nova empresa",
            description = "Registra uma nova empresa (tenant) no sistema. Cada empresa opera de forma isolada " +
                    "com seus proprios dados, usuarios e configuracoes. O CNPJ deve ser unico no sistema. " +
                    "Opcionalmente, pode-se informar a chave da API Asaas para integracao com o gateway de pagamentos.")
    public ResponseEntity<CompanyResponse> create(@Valid @RequestBody CreateCompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(companyService.create(request));
    }

    @GetMapping
    @Operation(summary = "Listar todas as empresas",
            description = "Retorna uma lista paginada de todas as empresas cadastradas no sistema. " +
                    "Suporta paginacao via query params (page, size, sort). " +
                    "Exemplo: ?page=0&size=20&sort=nomeFantasia,asc")
    public ResponseEntity<Page<CompanyResponse>> findAll(Pageable pageable) {
        return ResponseEntity.ok(companyService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar empresa por ID",
            description = "Retorna os dados completos de uma empresa especifica pelo seu identificador. " +
                    "Retorna 404 caso a empresa nao seja encontrada.")
    public ResponseEntity<CompanyResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(companyService.findById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar dados da empresa",
            description = "Atualiza os dados cadastrais de uma empresa existente. Apenas os campos enviados no body " +
                    "serao atualizados (atualizacao parcial). Campos como razao social, nome fantasia, email, " +
                    "telefone, status, politica de mudanca de plano e estrategia de downgrade podem ser alterados.")
    public ResponseEntity<CompanyResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateCompanyRequest request) {
        return ResponseEntity.ok(companyService.update(id, request));
    }

    @PutMapping("/{id}/credentials")
    @Operation(summary = "Atualizar credenciais Asaas",
            description = "Atualiza a chave da API Asaas e o ambiente (SANDBOX ou PRODUCTION) de uma empresa. " +
                    "A chave e armazenada de forma criptografada no banco de dados usando Jasypt. " +
                    "Apos atualizar, utilize o endpoint de teste de conexao para validar as credenciais.")
    public ResponseEntity<Void> updateCredentials(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateCredentialsRequest request) {
        companyService.updateCredentials(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test-connection")
    @Operation(summary = "Testar conexao com a API Asaas",
            description = "Testa a conexao com a API do Asaas usando as credenciais armazenadas da empresa. " +
                    "Faz uma chamada ao endpoint /myAccount do Asaas para verificar se a chave da API e valida " +
                    "e se o ambiente configurado (sandbox/producao) esta acessivel. " +
                    "Retorna success=true se a conexao for bem-sucedida ou success=false em caso de falha.")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable Long id) {
        boolean success = companyService.testConnection(id);
        return ResponseEntity.ok(Map.of(
                "success", success,
                "message", success ? "Conexao realizada com sucesso" : "Falha na conexao"
        ));
    }
}
