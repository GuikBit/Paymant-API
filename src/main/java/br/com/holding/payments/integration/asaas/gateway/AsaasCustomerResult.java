package br.com.holding.payments.integration.asaas.gateway;

public record AsaasCustomerResult(
        String asaasId,
        String name,
        String cpfCnpj,
        String email,
        String phone,
        String personType
) {}
