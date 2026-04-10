package br.com.holding.payments.coupon.dto;

import br.com.holding.payments.coupon.CouponApplicationType;
import br.com.holding.payments.coupon.CouponScope;
import br.com.holding.payments.coupon.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateCouponRequest(
        @NotBlank(message = "Codigo do cupom e obrigatorio")
        @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Codigo deve conter apenas letras maiusculas, numeros, hifen e underscore")
        String code,
        String description,
        @NotNull(message = "Tipo de desconto e obrigatorio") DiscountType discountType,
        @NotNull(message = "Valor do desconto e obrigatorio") @Positive(message = "Valor do desconto deve ser positivo") BigDecimal discountValue,
        @NotNull(message = "Escopo do cupom e obrigatorio") CouponScope scope,
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
