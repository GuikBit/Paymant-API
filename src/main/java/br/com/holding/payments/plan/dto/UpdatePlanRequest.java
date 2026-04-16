package br.com.holding.payments.plan.dto;

import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
        @Valid List<PlanLimitDto> limits,
        @Valid List<PlanLimitDto> features,
        Integer tierOrder,
        Boolean isFree
) {}
