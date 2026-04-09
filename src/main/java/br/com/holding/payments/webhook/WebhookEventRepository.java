package br.com.holding.payments.webhook;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    Optional<WebhookEvent> findByAsaasEventIdAndCompanyId(String asaasEventId, Long companyId);

    @Query(value = """
            SELECT * FROM webhook_events
            WHERE status IN ('PENDING', 'DEFERRED')
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
            ORDER BY received_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<WebhookEvent> findEventsToProcess(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    @Modifying
    @Query(value = """
            UPDATE webhook_events SET status = 'PENDING', next_attempt_at = NOW()
            WHERE status = 'DEFERRED'
              AND payload::text LIKE '%' || :asaasId || '%'
            """, nativeQuery = true)
    int markDeferredReadyByAsaasId(@Param("asaasId") String asaasId);

    Page<WebhookEvent> findByStatus(WebhookEventStatus status, Pageable pageable);

    Page<WebhookEvent> findByCompanyId(Long companyId, Pageable pageable);

    long countByStatus(WebhookEventStatus status);

    @Query("SELECT COUNT(e) FROM WebhookEvent e WHERE e.status = 'DLQ'")
    long countDlq();

    @Query("SELECT COUNT(e) FROM WebhookEvent e WHERE e.status = 'DEFERRED'")
    long countDeferred();

    @Query(value = """
            SELECT EXTRACT(EPOCH FROM (NOW() - MIN(received_at)))
            FROM webhook_events
            WHERE status IN ('PENDING', 'DEFERRED')
            """, nativeQuery = true)
    Double calculateLagSeconds();
}
