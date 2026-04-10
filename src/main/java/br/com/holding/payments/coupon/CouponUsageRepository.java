package br.com.holding.payments.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {

    Page<CouponUsage> findByCouponId(Long couponId, Pageable pageable);

    long countByCouponIdAndCustomerId(Long couponId, Long customerId);

    @Query("SELECT COUNT(cu) FROM CouponUsage cu WHERE cu.coupon.id = :couponId AND cu.customer.id = :customerId AND cu.chargeId IS NOT NULL")
    long countChargeUsagesByCustomer(@Param("couponId") Long couponId, @Param("customerId") Long customerId);
}
