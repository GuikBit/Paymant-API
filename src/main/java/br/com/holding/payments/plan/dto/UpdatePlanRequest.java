package br.com.holding.payments.plan.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UpdatePlanRequest(
        String name,
        String description,
        BigDecimal precoMensal,
        BigDecimal precoAnual,
        BigDecimal descontoPercentualAnual,
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
        String limits,
        String features,
        Integer tierOrder
) {}
