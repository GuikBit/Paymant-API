package br.com.holding.payments.integration.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasAccountResponse(
        String id,
        String name,
        String email,
        String cpfCnpj,
        String personType
) {}
