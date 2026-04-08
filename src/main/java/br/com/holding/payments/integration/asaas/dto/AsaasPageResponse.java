package br.com.holding.payments.integration.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasPageResponse<T>(
        boolean hasMore,
        int totalCount,
        int limit,
        int offset,
        List<T> data
) {}
