package br.com.holding.payments.coupon;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CouponCalculator {

    private static final BigDecimal MAX_DISCOUNT_PERCENT = new BigDecimal("90");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_DISCOUNT_FACTOR = new BigDecimal("0.90");

    private CouponCalculator() {}

    public static CouponDiscountResult calculate(DiscountType type, BigDecimal discountValue, BigDecimal originalValue) {
        if (originalValue == null || originalValue.compareTo(BigDecimal.ZERO) <= 0) {
            return new CouponDiscountResult(BigDecimal.ZERO, originalValue != null ? originalValue : BigDecimal.ZERO);
        }

        BigDecimal discount;

        if (type == DiscountType.PERCENTAGE) {
            BigDecimal cappedPercent = discountValue.min(MAX_DISCOUNT_PERCENT);
            discount = originalValue
                    .multiply(cappedPercent)
                    .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        } else {
            BigDecimal maxDiscount = originalValue.multiply(MAX_DISCOUNT_FACTOR).setScale(2, RoundingMode.HALF_UP);
            discount = discountValue.min(maxDiscount);
        }

        BigDecimal finalValue = originalValue.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        if (finalValue.compareTo(BigDecimal.ZERO) <= 0) {
            finalValue = originalValue.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
            discount = originalValue.subtract(finalValue);
        }

        return new CouponDiscountResult(discount, finalValue);
    }

    public record CouponDiscountResult(
            BigDecimal discountAmount,
            BigDecimal finalValue
    ) {}
}
