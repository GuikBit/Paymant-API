package br.com.holding.payments.coupon.dto;

import br.com.holding.payments.coupon.CouponScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ValidateCouponRequest(
        @NotBlank(message = "Codigo do cupom e obrigatorio") String couponCode,
        @NotNull(message = "Escopo e obrigatorio") CouponScope scope,
        String planCode,
        String cycle,
        BigDecimal value,
        Long customerId
) {}
