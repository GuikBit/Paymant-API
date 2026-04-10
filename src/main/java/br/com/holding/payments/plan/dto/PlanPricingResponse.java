package br.com.holding.payments.plan.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PlanPricingResponse(
        Long id,
        String codigo,
        String name,
        BigDecimal precoMensal,
        BigDecimal precoSemestral,
        BigDecimal precoAnual,
        BigDecimal descontoPercentualAnual,
        PromoPricing promoMensal,
        PromoPricing promoAnual,
        String features,
        String limits
) {

    public record PromoPricing(
            boolean ativa,
            BigDecimal preco,
            String texto,
            LocalDateTime validaAte
    ) {}
}
