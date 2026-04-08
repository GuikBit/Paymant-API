package br.com.holding.payments.outbox;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(value = "SELECT * FROM outbox WHERE status = 'PENDING' OR status = 'FAILED' " +
            "ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEvent> findPendingEventsForProcessing(@Param("limit") int limit);

    Page<OutboxEvent> findByStatus(OutboxStatus status, Pageable pageable);

    @Query("SELECT COUNT(e) FROM OutboxEvent e WHERE e.status = :status")
    long countByStatus(@Param("status") OutboxStatus status);

    @Query(value = "SELECT EXTRACT(EPOCH FROM (NOW() - MIN(created_at))) FROM outbox WHERE status = 'PENDING'",
            nativeQuery = true)
    Double calculateLagSeconds();
}
