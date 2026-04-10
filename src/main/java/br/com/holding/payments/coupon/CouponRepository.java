package br.com.holding.payments.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCodeAndCompanyId(String code, Long companyId);

    boolean existsByCodeAndCompanyId(String code, Long companyId);

    @Query("SELECT c FROM Coupon c WHERE c.active = true AND c.deletedAt IS NULL")
    Page<Coupon> findAllActive(Pageable pageable);

    @Modifying
    @Query("UPDATE Coupon c SET c.usageCount = c.usageCount + 1 WHERE c.id = :id AND (c.maxUses IS NULL OR c.usageCount < c.maxUses)")
    int incrementUsageCount(@Param("id") Long id);

    @Query(value = "SELECT * FROM coupons WHERE id = :id", nativeQuery = true)
    Optional<Coupon> findByIdIncludingDeleted(@Param("id") Long id);
}
