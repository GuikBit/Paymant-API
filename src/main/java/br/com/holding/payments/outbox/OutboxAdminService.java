package br.com.holding.payments.outbox;

import br.com.holding.payments.common.errors.ResourceNotFoundException;
import br.com.holding.payments.outbox.dto.OutboxEventResponse;
import br.com.holding.payments.outbox.dto.OutboxSummaryResponse;
import br.com.holding.payments.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxAdminService {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional(readOnly = true)
    public Page<OutboxEventResponse> findByStatus(OutboxStatus status, Pageable pageable) {
        Long companyId = TenantContext.getRequiredCompanyId();
        return outboxEventRepository.findByStatusAndCompanyId(status, companyId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public OutboxSummaryResponse getSummary() {
        Long companyId = TenantContext.getRequiredCompanyId();
        return new OutboxSummaryResponse(
                outboxEventRepository.countByStatusAndCompanyId(OutboxStatus.PENDING, companyId),
                outboxEventRepository.countByStatusAndCompanyId(OutboxStatus.PUBLISHED, companyId),
                outboxEventRepository.countByStatusAndCompanyId(OutboxStatus.FAILED, companyId),
                outboxEventRepository.countByStatusAndCompanyId(OutboxStatus.DLQ, companyId),
                outboxEventRepository.calculateLagSecondsByCompanyId(companyId)
        );
    }

    @Transactional
    public void retryEvent(Long eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("OutboxEvent", eventId));

        if (event.getStatus() != OutboxStatus.FAILED && event.getStatus() != OutboxStatus.DLQ) {
            throw new IllegalStateException("Only FAILED or DLQ events can be retried");
        }

        event.setStatus(OutboxStatus.PENDING);
        event.setAttemptCount(0);
        event.setLastError(null);
        outboxEventRepository.save(event);
    }

    private OutboxEventResponse toResponse(OutboxEvent event) {
        return new OutboxEventResponse(
                event.getId(),
                event.getCompany().getId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                event.getPayload(),
                event.getStatus(),
                event.getAttemptCount(),
                event.getCreatedAt(),
                event.getPublishedAt(),
                event.getLastError()
        );
    }
}
