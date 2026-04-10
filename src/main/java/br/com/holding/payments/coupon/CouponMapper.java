package br.com.holding.payments.coupon;

import br.com.holding.payments.coupon.dto.CouponResponse;
import br.com.holding.payments.coupon.dto.CouponUsageResponse;
import org.springframework.stereotype.Component;

@Component
public class CouponMapper {

    public CouponResponse toResponse(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCompany().getId(),
                coupon.getCode(),
                coupon.getDescription(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getScope(),
                coupon.getApplicationType(),
                coupon.getRecurrenceMonths(),
                coupon.getValidFrom(),
                coupon.getValidUntil(),
                coupon.getMaxUses(),
                coupon.getMaxUsesPerCustomer(),
                coupon.getUsageCount(),
                coupon.getAllowedPlans(),
                coupon.getAllowedCustomers(),
                coupon.getAllowedCycle(),
                coupon.getActive(),
                coupon.isCurrentlyValid(),
                coupon.getCreatedAt(),
                coupon.getUpdatedAt()
        );
    }

    public CouponUsageResponse toUsageResponse(CouponUsage usage) {
        return new CouponUsageResponse(
                usage.getId(),
                usage.getCoupon().getId(),
                usage.getCouponCode() != null ? usage.getCouponCode() : usage.getCoupon().getCode(),
                usage.getCustomer().getId(),
                usage.getSubscriptionId(),
                usage.getChargeId(),
                usage.getOriginalValue(),
                usage.getDiscountAmount(),
                usage.getFinalValue(),
                usage.getPlanCode(),
                usage.getCycle(),
                usage.getUsedAt()
        );
    }
}
