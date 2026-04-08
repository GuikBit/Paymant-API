package br.com.holding.payments.phase1;

import br.com.holding.payments.AbstractIntegrationTest;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Fase 1 - Isolamento RLS entre empresas")
class RlsIsolationTest extends AbstractIntegrationTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Company companyA;
    private Company companyB;

    @BeforeEach
    void setup() {
        companyA = companyRepository.findByCnpj("11111111000111")
                .orElseGet(() -> companyRepository.save(Company.builder()
                        .cnpj("11111111000111")
                        .razaoSocial("Empresa A LTDA")
                        .nomeFantasia("Empresa A")
                        .email("a@test.com")
                        .build()));

        companyB = companyRepository.findByCnpj("22222222000222")
                .orElseGet(() -> companyRepository.save(Company.builder()
                        .cnpj("22222222000222")
                        .razaoSocial("Empresa B LTDA")
                        .nomeFantasia("Empresa B")
                        .email("b@test.com")
                        .build()));
    }

    @Test
    @DisplayName("Empresa A nao enxerga dados da Empresa B com RLS ativo")
    void companyA_shouldNotSee_companyB_data() {
        String tagA = "RLS_A_" + System.nanoTime();
        String tagB = "RLS_B_" + System.nanoTime();

        // Insert data for both companies
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "SET LOCAL app.current_company_id = '" + companyA.getId() + "'"
            ).executeUpdate();
            entityManager.createNativeQuery(
                    "INSERT INTO audit_log (company_id, actor, action, entity, entity_id, created_at) " +
                    "VALUES (:cid, 'test', :tag, 'Company', :cid, NOW())")
                    .setParameter("cid", companyA.getId())
                    .setParameter("tag", tagA)
                    .executeUpdate();
            return null;
        });

        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "SET LOCAL app.current_company_id = '" + companyB.getId() + "'"
            ).executeUpdate();
            entityManager.createNativeQuery(
                    "INSERT INTO audit_log (company_id, actor, action, entity, entity_id, created_at) " +
                    "VALUES (:cid, 'test', :tag, 'Company', :cid, NOW())")
                    .setParameter("cid", companyB.getId())
                    .setParameter("tag", tagB)
                    .executeUpdate();
            return null;
        });

        // Query as company A
        List<?> resultsA = transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "SET LOCAL app.current_company_id = '" + companyA.getId() + "'"
            ).executeUpdate();
            return entityManager.createNativeQuery(
                    "SELECT action FROM audit_log WHERE action IN (:tagA, :tagB)")
                    .setParameter("tagA", tagA)
                    .setParameter("tagB", tagB)
                    .getResultList();
        });

        assertThat(resultsA).hasSize(1);
        assertThat(resultsA.get(0).toString()).isEqualTo(tagA);

        // Query as company B
        List<?> resultsB = transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "SET LOCAL app.current_company_id = '" + companyB.getId() + "'"
            ).executeUpdate();
            return entityManager.createNativeQuery(
                    "SELECT action FROM audit_log WHERE action IN (:tagA, :tagB)")
                    .setParameter("tagA", tagA)
                    .setParameter("tagB", tagB)
                    .getResultList();
        });

        assertThat(resultsB).hasSize(1);
        assertThat(resultsB.get(0).toString()).isEqualTo(tagB);
    }

    @Test
    @DisplayName("Query sem company_id setado retorna vazio (RLS filtra tudo)")
    void query_withoutCompanyId_shouldReturnEmpty() {
        String tag = "RLS_EMPTY_" + System.nanoTime();

        // Insert data for company A
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "SET LOCAL app.current_company_id = '" + companyA.getId() + "'"
            ).executeUpdate();
            entityManager.createNativeQuery(
                    "INSERT INTO audit_log (company_id, actor, action, entity, entity_id, created_at) " +
                    "VALUES (:cid, 'test', :tag, 'Company', :cid, NOW())")
                    .setParameter("cid", companyA.getId())
                    .setParameter("tag", tag)
                    .executeUpdate();
            return null;
        });

        // Query without setting company_id (use impossible ID)
        List<?> results = transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "SET LOCAL app.current_company_id = '999999999'"
            ).executeUpdate();
            return entityManager.createNativeQuery(
                    "SELECT action FROM audit_log WHERE action = :tag")
                    .setParameter("tag", tag)
                    .getResultList();
        });

        assertThat(results).isEmpty();
    }
}
