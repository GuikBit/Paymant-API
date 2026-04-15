package br.com.holding.payments.plan.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
        List<PlanLimitDto> features,
        List<PlanLimitDto> limits
) {

    public record PromoPricing(
            boolean ativa,
            BigDecimal preco,
            String texto,
            LocalDateTime validaAte
    ) {}
}
