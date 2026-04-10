package br.com.holding.payments.coupon.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponUsageResponse(
        Long id,
        Long couponId,
        String couponCode,
        Long customerId,
        Long subscriptionId,
        Long chargeId,
        BigDecimal originalValue,
        BigDecimal discountAmount,
        BigDecimal finalValue,
        String planCode,
        String cycle,
        LocalDateTime usedAt
) {}
