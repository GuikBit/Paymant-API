package br.com.holding.payments.plan.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PlanResponse(
        Long id,
        Long companyId,
        String name,
        String description,
        String codigo,
        BigDecimal precoMensal,
        BigDecimal precoAnual,
        BigDecimal descontoPercentualAnual,
        BigDecimal precoSemestral,
        Boolean promoMensalAtiva,
        BigDecimal promoMensalPreco,
        String promoMensalTexto,
        LocalDateTime promoMensalInicio,
        LocalDateTime promoMensalFim,
        Boolean promoAnualAtiva,
        BigDecimal promoAnualPreco,
        String promoAnualTexto,
        LocalDateTime promoAnualInicio,
        LocalDateTime promoAnualFim,
        Integer trialDays,
        BigDecimal setupFee,
        Boolean active,
        Integer version,
        String limits,
        String features,
        Integer tierOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
