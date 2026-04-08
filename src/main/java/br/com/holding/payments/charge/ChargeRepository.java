package br.com.holding.payments.charge;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ChargeRepository extends JpaRepository<Charge, Long> {

    Optional<Charge> findByAsaasId(String asaasId);

    @Query("SELECT c FROM Charge c WHERE " +
            "(:status IS NULL OR c.status = :status) AND " +
            "(:origin IS NULL OR c.origin = :origin) AND " +
            "(:dueDateFrom IS NULL OR c.dueDate >= :dueDateFrom) AND " +
            "(:dueDateTo IS NULL OR c.dueDate <= :dueDateTo) AND " +
            "(:customerId IS NULL OR c.customer.id = :customerId)")
    Page<Charge> findWithFilters(
            @Param("status") ChargeStatus status,
            @Param("origin") ChargeOrigin origin,
            @Param("dueDateFrom") LocalDate dueDateFrom,
            @Param("dueDateTo") LocalDate dueDateTo,
            @Param("customerId") Long customerId,
            Pageable pageable);

    Page<Charge> findBySubscriptionId(Long subscriptionId, Pageable pageable);
}
