package br.com.holding.payments.webhooksubscription;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, Long> {

    Page<WebhookSubscription> findByCompanyId(Long companyId, Pageable pageable);

    Optional<WebhookSubscription> findByIdAndCompanyId(Long id, Long companyId);

    long countByCompanyIdAndActiveTrue(Long companyId);

    @Query(value = """
            SELECT * FROM webhook_subscriptions
            WHERE company_id = :companyId
              AND active = TRUE
              AND ('*' = ANY(event_types) OR :eventType = ANY(event_types))
            """, nativeQuery = true)
    List<WebhookSubscription> findActiveMatchingEvent(
            @Param("companyId") Long companyId,
            @Param("eventType") String eventType);
}
