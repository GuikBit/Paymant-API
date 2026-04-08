package br.com.holding.payments.phase1;

import br.com.holding.payments.AbstractIntegrationTest;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.company.EncryptionService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Fase 1 - Criptografia da asaas_api_key")
class EncryptionTest extends AbstractIntegrationTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("asaas_api_key esta cifrada no banco e clara em memoria")
    void apiKey_shouldBeEncryptedInDb_andDecryptedInMemory() {
        String plainApiKey = "aact_test_key_123456789";
        String testCnpj = "99999999000199";

        // Create or update company with encrypted API key
        Company company = transactionTemplate.execute(status -> {
            Company existing = companyRepository.findByCnpj(testCnpj).orElse(null);
            if (existing != null) {
                existing.setAsaasApiKeyEncrypted(encryptionService.encrypt(plainApiKey));
                return companyRepository.save(existing);
            }
            Company c = Company.builder()
                    .cnpj(testCnpj)
                    .razaoSocial("Crypto Test LTDA")
                    .nomeFantasia("Crypto Test")
                    .email("crypto@test.com")
                    .asaasApiKeyEncrypted(encryptionService.encrypt(plainApiKey))
                    .build();
            return companyRepository.save(c);
        });

        // Read raw value from database via native query
        String rawValueInDb = transactionTemplate.execute(status -> {
            return (String) entityManager.createNativeQuery(
                    "SELECT asaas_api_key_encrypted FROM companies WHERE id = :id"
            ).setParameter("id", company.getId()).getSingleResult();
        });

        // Encrypted value in DB should NOT be the plain text
        assertThat(rawValueInDb).isNotNull();
        assertThat(rawValueInDb).isNotEqualTo(plainApiKey);
        assertThat(rawValueInDb).doesNotContain("aact_test_key");

        // Decrypted value should match original
        String decrypted = encryptionService.decrypt(rawValueInDb);
        assertThat(decrypted).isEqualTo(plainApiKey);
    }

    @Test
    @DisplayName("Encrypt e decrypt de valor nulo retorna nulo")
    void encrypt_null_shouldReturnNull() {
        assertThat(encryptionService.encrypt(null)).isNull();
        assertThat(encryptionService.decrypt(null)).isNull();
        assertThat(encryptionService.encrypt("")).isNull();
        assertThat(encryptionService.decrypt("")).isNull();
    }
}
