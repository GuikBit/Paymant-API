package br.com.holding.payments.apikey;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    @Query(value = """
            SELECT ak.* FROM api_keys ak
            WHERE ak.key_hash = :keyHash AND ak.active = TRUE
            """, nativeQuery = true)
    Optional<ApiKey> findByKeyHashAndActiveTrue(@Param("keyHash") String keyHash);

    Page<ApiKey> findByCompanyId(Long companyId, Pageable pageable);

    @Modifying
    @Query("UPDATE ApiKey a SET a.lastUsedAt = CURRENT_TIMESTAMP WHERE a.id = :id")
    void updateLastUsedAt(@Param("id") Long id);
}
