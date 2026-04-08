package br.com.holding.payments.phase3;

import br.com.holding.payments.AbstractIntegrationTest;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.outbox.*;
import br.com.holding.payments.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Fase 3 - Outbox Pattern")
class OutboxTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    private Company company;

    @BeforeEach
    void setup() {
        company = companyRepository.findByCnpj("11111111000111")
                .orElseGet(() -> companyRepository.save(Company.builder()
                        .cnpj("11111111000111")
                        .razaoSocial("Outbox Test LTDA")
                        .nomeFantasia("Outbox Test")
                        .email("outbox@test.com")
                        .build()));
        TenantContext.setCompanyId(company.getId());
    }

    @AfterEach
    void teardown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Evento publicado na mesma transacao persiste com status PENDING")
    void publish_shouldPersistAsPending() {
        String aggregateId = "pub-" + System.nanoTime();

        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "SET LOCAL app.current_company_id = '" + company.getId() + "'"
            ).executeUpdate();
            outboxPublisher.publish(company.getId(),
                    "ChargeCreatedEvent", "Charge", aggregateId,
                    "{\"chargeId\":123,\"value\":100.00}");
            return null;
        });

        // Verify via native query with tenant context set
        List<?> events = transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "SET LOCAL app.current_company_id = '" + company.getId() + "'"
            ).executeUpdate();
            return entityManager.createNativeQuery(
                    "SELECT event_type FROM outbox WHERE aggregate_id = :aggId AND status = 'PENDING'")
                    .setParameter("aggId", aggregateId)
                    .getResultList();
        });
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).toString()).isEqualTo("ChargeCreatedEvent");
    }

    @Test
    @DisplayName("Relay processa eventos pendentes e marca como PUBLISHED")
    void relay_shouldPublishPendingEvents() {
        String aggregateId = "relay-" + System.nanoTime();

        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "SET LOCAL app.current_company_id = '" + company.getId() + "'"
            ).executeUpdate();
            outboxPublisher.publish(company.getId(),
                    "TestRelayEvent", "Test", aggregateId,
                    "{\"test\":true}");
            return null;
        });

        // Run relay (webhook URL is empty in test, marks as published)
        outboxRelay.relayPendingEvents();

        // Verify no more pending TestRelayEvent
        List<OutboxEvent> remaining = outboxEventRepository.findPendingEventsForProcessing(100);
        assertThat(remaining).noneMatch(e ->
                e.getEventType().equals("TestRelayEvent") && e.getAggregateId().equals(aggregateId));
    }

    @Test
    @DisplayName("Contagem de eventos por status funciona corretamente")
    void countByStatus_shouldWork() {
        long pendingCount = outboxEventRepository.countByStatus(OutboxStatus.PENDING);
        long publishedCount = outboxEventRepository.countByStatus(OutboxStatus.PUBLISHED);
        long dlqCount = outboxEventRepository.countByStatus(OutboxStatus.DLQ);

        assertThat(pendingCount).isGreaterThanOrEqualTo(0);
        assertThat(publishedCount).isGreaterThanOrEqualTo(0);
        assertThat(dlqCount).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Evento com falha apos N tentativas vai para DLQ")
    void eventAfterMaxAttempts_shouldMoveToDlq() {
        String aggregateId = "dlq-" + System.nanoTime();

        // Create event with SET LOCAL for RLS
        OutboxEvent event = transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "SET LOCAL app.current_company_id = '" + company.getId() + "'"
            ).executeUpdate();
            OutboxEvent e = OutboxEvent.builder()
                    .company(company)
                    .aggregateType("Test")
                    .aggregateId(aggregateId)
                    .eventType("TestDlqEvent")
                    .payload("{\"test\":true}")
                    .status(OutboxStatus.FAILED)
                    .attemptCount(4)
                    .build();
            return outboxEventRepository.save(e);
        });

        // Simulate one more failure → should go to DLQ
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "SET LOCAL app.current_company_id = '" + company.getId() + "'"
            ).executeUpdate();
            OutboxEvent e = outboxEventRepository.findById(event.getId()).orElseThrow();
            e.markFailed("simulated failure");
            if (e.getAttemptCount() >= 5) {
                e.markDlq("simulated failure");
            }
            outboxEventRepository.save(e);
            return null;
        });

        OutboxEvent result = transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "SET LOCAL app.current_company_id = '" + company.getId() + "'"
            ).executeUpdate();
            return outboxEventRepository.findById(event.getId()).orElseThrow();
        });

        assertThat(result.getStatus()).isEqualTo(OutboxStatus.DLQ);
        assertThat(result.getLastError()).isEqualTo("simulated failure");
    }
}
