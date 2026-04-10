package br.com.holding.payments.coupon.dto;

import br.com.holding.payments.coupon.CouponApplicationType;
import br.com.holding.payments.coupon.DiscountType;

import java.math.BigDecimal;

public record CouponValidationResponse(
        boolean valid,
        String message,
        DiscountType discountType,
        CouponApplicationType applicationType,
        BigDecimal percentualDiscount,
        BigDecimal discountAmount,
        BigDecimal originalValue,
        BigDecimal finalValue
) {
    public static CouponValidationResponse invalid(String message) {
        return new CouponValidationResponse(false, message, null, null, null, null, null, null);
    }
}
