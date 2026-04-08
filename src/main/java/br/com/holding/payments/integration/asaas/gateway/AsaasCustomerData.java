package br.com.holding.payments.integration.asaas.gateway;

public record AsaasCustomerData(
        String name,
        String cpfCnpj,
        String email,
        String phone,
        String mobilePhone,
        String address,
        String addressNumber,
        String complement,
        String province,
        String postalCode,
        String externalReference
) {}
