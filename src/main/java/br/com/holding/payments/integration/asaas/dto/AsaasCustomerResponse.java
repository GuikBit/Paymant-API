package br.com.holding.payments.integration.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasCustomerResponse(
        String id,
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
        String externalReference,
        Boolean notificationDisabled,
        String city,
        String state,
        String country,
        String personType,
        Boolean deleted,
        String dateCreated
) {}
