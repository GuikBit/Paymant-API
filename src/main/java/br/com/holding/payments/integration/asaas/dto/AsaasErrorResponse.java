package br.com.holding.payments.integration.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasErrorResponse(
        List<AsaasError> errors
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AsaasError(
            String code,
            String description
    ) {}
}
