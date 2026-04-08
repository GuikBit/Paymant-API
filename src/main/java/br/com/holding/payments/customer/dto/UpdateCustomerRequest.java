package br.com.holding.payments.customer.dto;

public record UpdateCustomerRequest(
        String name,
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
