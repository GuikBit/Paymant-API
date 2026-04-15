package br.com.holding.payments.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreatePlanRequest(
        @NotBlank(message = "Nome do plano e obrigatorio") String name,
        String description,
        @NotBlank(message = "Codigo do plano e obrigatorio")
        @Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "Codigo deve conter apenas letras minusculas, numeros e hifen, iniciando com letra")
        String codigo,
        @NotNull(message = "Preco mensal e obrigatorio")
        @PositiveOrZero(message = "Preco mensal nao pode ser negativo")
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
