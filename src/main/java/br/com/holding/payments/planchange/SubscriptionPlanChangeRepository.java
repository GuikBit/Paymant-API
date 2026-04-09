package br.com.holding.payments.planchange;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanChangeRepository extends JpaRepository<SubscriptionPlanChange, Long> {

    Page<SubscriptionPlanChange> findBySubscriptionId(Long subscriptionId, Pageable pageable);

    Optional<SubscriptionPlanChange> findByChargeId(Long chargeId);

    @Query("SELECT pc FROM SubscriptionPlanChange pc WHERE pc.status = 'SCHEDULED' AND pc.scheduledFor <= :now")
    List<SubscriptionPlanChange> findScheduledReadyToProcess(@Param("now") LocalDateTime now);

    @Query("SELECT pc FROM SubscriptionPlanChange pc WHERE pc.subscription.id = :subscriptionId AND pc.status IN ('PENDING', 'AWAITING_PAYMENT', 'SCHEDULED')")
    Optional<SubscriptionPlanChange> findPendingBySubscriptionId(@Param("subscriptionId") Long subscriptionId);
}
