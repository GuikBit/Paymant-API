package br.com.holding.payments.apikey.dto;

public record CreateApiKeyResponse(
        ApiKeyResponse apiKey,
        String rawKey
) {}
