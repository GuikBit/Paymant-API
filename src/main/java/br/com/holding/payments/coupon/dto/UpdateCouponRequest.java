package br.com.holding.payments.coupon.dto;

import br.com.holding.payments.coupon.CouponApplicationType;
import br.com.holding.payments.coupon.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UpdateCouponRequest(
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        CouponApplicationType applicationType,
        Integer recurrenceMonths,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        Integer maxUses,
        Integer maxUsesPerCustomer,
        String allowedPlans,
        String allowedCustomers,
        String allowedCycle
) {}
