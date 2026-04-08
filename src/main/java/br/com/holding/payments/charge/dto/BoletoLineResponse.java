package br.com.holding.payments.charge.dto;

public record BoletoLineResponse(
        String identificationField,
        String nossoNumero,
        String barCode
) {}
