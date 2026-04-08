package br.com.holding.payments.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    @Query("SELECT ik FROM IdempotencyKey ik WHERE ik.company.id = :companyId AND ik.endpoint = :endpoint AND ik.key = :key")
    Optional<IdempotencyKey> findByCompanyIdAndEndpointAndKey(
            @Param("companyId") Long companyId,
            @Param("endpoint") String endpoint,
            @Param("key") String key);

    @Modifying
    @Query("DELETE FROM IdempotencyKey ik WHERE ik.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
