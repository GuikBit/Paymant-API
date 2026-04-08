package br.com.holding.payments.integration.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasBoletoResponse(
        String identificationField,
        String nossoNumero,
        String barCode
) {}
