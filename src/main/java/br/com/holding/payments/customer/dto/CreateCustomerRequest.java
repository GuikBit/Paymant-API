package br.com.holding.payments.customer.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCustomerRequest(
        @NotBlank(message = "Nome e obrigatorio") String name,
        @NotBlank(message = "Documento (CPF/CNPJ) e obrigatorio") String document,
        String email,
        String phone,
        String addressStreet,
        String addressNumber,
        String addressComplement,
        String addressNeighborhood,
        String addressCity,
        String addressState,
        String addressPostalCode
) {}
