package br.com.holding.payments.asaassync;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AsaasSyncPendingRepository extends JpaRepository<AsaasSyncPending, Long> {

    @Query(value = "SELECT * FROM asaas_sync_pending " +
            "WHERE status = 'PENDING' AND next_attempt_at <= NOW() " +
            "ORDER BY next_attempt_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<AsaasSyncPending> findReadyForRetry(@Param("limit") int limit);

    Page<AsaasSyncPending> findByStatus(AsaasSyncStatus status, Pageable pageable);

    Page<AsaasSyncPending> findByStatusAndCompanyId(AsaasSyncStatus status, Long companyId, Pageable pageable);

    @Query("SELECT COUNT(e) FROM AsaasSyncPending e WHERE e.status = :status")
    long countByStatus(@Param("status") AsaasSyncStatus status);
}
