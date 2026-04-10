package br.com.holding.payments.coupon.dto;

import br.com.holding.payments.coupon.CouponApplicationType;
import br.com.holding.payments.coupon.CouponScope;
import br.com.holding.payments.coupon.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponResponse(
        Long id,
        Long companyId,
        String code,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        CouponScope scope,
        CouponApplicationType applicationType,
        Integer recurrenceMonths,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        Integer maxUses,
        Integer maxUsesPerCustomer,
        Integer usageCount,
        String allowedPlans,
        String allowedCustomers,
        String allowedCycle,
        Boolean active,
        Boolean currentlyValid,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
