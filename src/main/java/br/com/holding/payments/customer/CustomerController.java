package br.com.holding.payments.customer;

import br.com.holding.payments.creditledger.CustomerCreditLedger;
import br.com.holding.payments.creditledger.CustomerCreditLedgerService;
import br.com.holding.payments.customer.dto.*;
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

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "Gerenciamento de clientes (pagadores) escopados por empresa. " +
        "Cada cliente e vinculado a uma empresa e sincronizado com o Asaas.")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerCreditLedgerService creditLedgerService;

    @PostMapping
    @Operation(summary = "Cadastrar novo cliente",
            description = "Cria um novo cliente vinculado a empresa do usuario autenticado. " +
                    "O cliente e automaticamente sincronizado com o Asaas. " +
                    "O documento (CPF/CNPJ) deve ser unico entre os clientes ativos da empresa.")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.create(request));
    }

    @GetMapping
    @Operation(summary = "Listar clientes",
            description = "Retorna lista paginada de clientes ativos da empresa. " +
                    "Suporta busca por nome, documento ou email via parametro 'search'. " +
                    "Exemplo: ?search=joao&page=0&size=20")
    public ResponseEntity<Page<CustomerResponse>> findAll(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(customerService.findAll(search, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar cliente por ID",
            description = "Retorna os dados completos de um cliente ativo. Retorna 404 se o cliente nao existir ou estiver deletado.")
    public ResponseEntity<CustomerResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.findById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar dados do cliente",
            description = "Atualiza os dados cadastrais do cliente. Apenas os campos enviados serao alterados (atualizacao parcial). " +
                    "As alteracoes sao sincronizadas automaticamente com o Asaas.")
    public ResponseEntity<CustomerResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody UpdateCustomerRequest request) {
        return ResponseEntity.ok(customerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir cliente (soft delete)",
            description = "Realiza exclusao logica do cliente (soft delete). O registro e mantido no banco com a data de exclusao " +
                    "para preservar integridade de cobrancas e faturas historicas. " +
                    "O cliente nao e removido do Asaas. O documento fica disponivel para recadastro.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customerService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('HOLDING_ADMIN', 'COMPANY_ADMIN')")
    @Operation(summary = "Restaurar cliente excluido",
            description = "Reverte a exclusao logica de um cliente. Apenas administradores podem realizar esta operacao. " +
                    "Falha se o documento ja estiver em uso por outro cliente ativo.")
    public ResponseEntity<CustomerResponse> restore(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.restore(id));
    }

    @PostMapping("/{id}/sync")
    @Operation(summary = "Sincronizar cliente com Asaas",
            description = "Forca a resincronizacao dos dados do cliente a partir do Asaas. " +
                    "Atualiza nome, email e telefone com os dados retornados pela API do Asaas.")
    public ResponseEntity<CustomerResponse> sync(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.syncFromAsaas(id));
    }

    @GetMapping("/{id}/credit-balance")
    @Operation(summary = "Consultar saldo de credito e extrato",
            description = "Retorna o saldo atual de credito do cliente e o historico de movimentacoes (append-only ledger).")
    public ResponseEntity<Map<String, Object>> getCreditBalance(
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable) {
        BigDecimal balance = creditLedgerService.getBalance(id);
        Page<CustomerCreditLedger> ledger = creditLedgerService.getLedger(id, pageable);
        return ResponseEntity.ok(Map.of("balance", balance, "ledger", ledger));
    }
}
