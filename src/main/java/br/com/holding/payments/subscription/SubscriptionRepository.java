package br.com.holding.payments.subscription;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByAsaasId(String asaasId);

    @Query("SELECT s FROM Subscription s WHERE " +
            "(:status IS NULL OR s.status = :status) AND " +
            "(:customerId IS NULL OR s.customer.id = :customerId)")
    Page<Subscription> findWithFilters(
            @Param("status") SubscriptionStatus status,
            @Param("customerId") Long customerId,
            Pageable pageable);

    List<Subscription> findByCustomerIdAndStatus(Long customerId, SubscriptionStatus status);

    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' AND s.plan.id = :planId")
    List<Subscription> findActiveByPlanId(@Param("planId") Long planId);

    @Query(value = """
            SELECT s.* FROM subscriptions s
            WHERE s.status = 'ACTIVE'
              AND EXISTS (
                SELECT 1 FROM charges c
                WHERE c.subscription_id = s.id
                  AND c.status = 'OVERDUE'
                GROUP BY c.subscription_id
                HAVING COUNT(*) >= :minOverdue
              )
            """, nativeQuery = true)
    List<Subscription> findActiveWithOverdueCharges(@Param("minOverdue") int minOverdue);

    long countByPlanIdAndStatus(Long planId, SubscriptionStatus status);
}
