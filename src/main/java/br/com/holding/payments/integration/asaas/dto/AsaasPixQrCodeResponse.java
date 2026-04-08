package br.com.holding.payments.integration.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasPixQrCodeResponse(
        Boolean success,
        String encodedImage,
        String payload,
        String expirationDate
) {}
