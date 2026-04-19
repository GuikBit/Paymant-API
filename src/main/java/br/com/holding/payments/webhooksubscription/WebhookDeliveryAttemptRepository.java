package br.com.holding.payments.webhooksubscription;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface WebhookDeliveryAttemptRepository extends JpaRepository<WebhookDeliveryAttempt, Long> {

    Page<WebhookDeliveryAttempt> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId, Pageable pageable);

    @Query(value = """
            SELECT * FROM webhook_delivery_attempts
            WHERE status = 'PENDING'
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<WebhookDeliveryAttempt> findReadyForDelivery(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);
}
