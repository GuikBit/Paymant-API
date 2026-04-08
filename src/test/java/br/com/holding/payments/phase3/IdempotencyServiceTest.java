package br.com.holding.payments.phase3;

import br.com.holding.payments.AbstractIntegrationTest;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.idempotency.CachedResponse;
import br.com.holding.payments.idempotency.IdempotencyConflictException;
import br.com.holding.payments.idempotency.IdempotencyService;
import br.com.holding.payments.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Fase 3 - Idempotencia")
class IdempotencyServiceTest extends AbstractIntegrationTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private CompanyRepository companyRepository;

    private Company company;

    @BeforeEach
    void setup() {
        company = companyRepository.findByCnpj("11111111000111")
                .orElseGet(() -> companyRepository.save(Company.builder()
                        .cnpj("11111111000111")
                        .razaoSocial("Idem Test LTDA")
                        .nomeFantasia("Idem Test")
                        .email("idem@test.com")
                        .build()));
        TenantContext.setCompanyId(company.getId());
    }

    @AfterEach
    void teardown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Primeiro request nao encontra resposta cacheada")
    void firstRequest_shouldReturnEmpty() {
        Optional<CachedResponse> result = idempotencyService.checkIdempotency(
                company.getId(), "/api/v1/charges/pix", "unique-key-001", "{\"value\":100}");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Segundo request identico retorna mesma resposta sem dupla escrita")
    void duplicateRequest_shouldReturnCachedResponse() {
        String endpoint = "/api/v1/charges/pix";
        String key = "dup-key-" + System.nanoTime();
        String body = "{\"customer\":\"cus_123\",\"value\":100}";
        String responseBody = "{\"id\":1,\"status\":\"PENDING\"}";

        // Save first response
        idempotencyService.saveResponse(company.getId(), endpoint, key, body, 201, responseBody);

        // Check again — should find cached response
        Optional<CachedResponse> cached = idempotencyService.checkIdempotency(company.getId(), endpoint, key, body);

        assertThat(cached).isPresent();
        assertThat(cached.get().getStatus()).isEqualTo(201);
        assertThat(cached.get().getBody()).isEqualTo(responseBody);
    }

    @Test
    @DisplayName("Mesma chave com body diferente retorna 422 (IdempotencyConflictException)")
    void sameKey_differentBody_shouldThrow422() {
        String endpoint = "/api/v1/charges/pix";
        String key = "conflict-key-" + System.nanoTime();
        String originalBody = "{\"customer\":\"cus_123\",\"value\":100}";
        String differentBody = "{\"customer\":\"cus_456\",\"value\":200}";

        // Save first response
        idempotencyService.saveResponse(company.getId(), endpoint, key, originalBody, 201, "{\"id\":1}");

        // Check with different body — should throw conflict
        assertThatThrownBy(() ->
                idempotencyService.checkIdempotency(company.getId(), endpoint, key, differentBody)
        ).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("Chaves de empresas diferentes sao independentes")
    void differentCompanies_shouldHaveIndependentKeys() {
        Company company2 = companyRepository.findByCnpj("22222222000222")
                .orElseGet(() -> companyRepository.save(Company.builder()
                        .cnpj("22222222000222")
                        .razaoSocial("Idem Test 2 LTDA")
                        .nomeFantasia("Idem Test 2")
                        .email("idem2@test.com")
                        .build()));

        String endpoint = "/api/v1/charges/pix";
        String key = "shared-key-" + System.nanoTime();
        String body = "{\"value\":100}";

        // Save for company 1
        idempotencyService.saveResponse(company.getId(), endpoint, key, body, 201, "{\"id\":1}");

        // Check for company 2 — should NOT find (switch tenant context)
        TenantContext.setCompanyId(company2.getId());
        Optional<CachedResponse> result = idempotencyService.checkIdempotency(company2.getId(), endpoint, key, body);
        assertThat(result).isEmpty();

        // Restore original tenant
        TenantContext.setCompanyId(company.getId());
    }

    @Test
    @DisplayName("Hash de body nulo nao causa erro")
    void nullBody_shouldNotThrow() {
        String endpoint = "/api/v1/test";
        String key = "null-body-" + System.nanoTime();

        // Should not throw when checking with null body
        Optional<CachedResponse> result = idempotencyService.checkIdempotency(
                company.getId(), endpoint, key, null);
        assertThat(result).isEmpty();

        // Should save successfully with null body
        idempotencyService.saveResponse(company.getId(), endpoint, key, null, 200, "{\"ok\":true}");
    }
}
