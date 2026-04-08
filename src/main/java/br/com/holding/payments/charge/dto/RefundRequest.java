package br.com.holding.payments.charge.dto;

import java.math.BigDecimal;

public record RefundRequest(
        BigDecimal value,
        String description
) {}
