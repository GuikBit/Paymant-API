package br.com.holding.payments.outbox;

import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final CompanyRepository companyRepository;
    private final ObjectMapper objectMapper;

    /**
     * Publishes a domain event to the outbox table.
     * Must be called within an existing @Transactional context to ensure atomicity.
     */
    public void publish(String eventType, String aggregateType, String aggregateId, Object payload) {
        Long companyId = TenantContext.getCompanyId();
        publish(companyId, eventType, aggregateType, aggregateId, payload);
    }

    public void publish(Long companyId, String eventType, String aggregateType, String aggregateId, Object payload) {
        try {
            String payloadJson = (payload instanceof String s) ? s : objectMapper.writeValueAsString(payload);
            Company company = companyRepository.getReferenceById(companyId);

            OutboxEvent event = OutboxEvent.builder()
                    .company(company)
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payloadJson)
                    .build();

            outboxEventRepository.save(event);
            log.debug("Outbox event published: type={}, aggregate={}#{}", eventType, aggregateType, aggregateId);
        } catch (Exception e) {
            log.error("Failed to publish outbox event: type={}, aggregate={}#{}", eventType, aggregateType, aggregateId, e);
            throw new RuntimeException("Failed to serialize outbox event payload", e);
        }
    }
}
