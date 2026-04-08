package br.com.holding.payments.customer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CustomerResponse(
        Long id,
        Long companyId,
        String asaasId,
        String name,
        String document,
        String email,
        String phone,
        String addressStreet,
        String addressNumber,
        String addressComplement,
        String addressNeighborhood,
        String addressCity,
        String addressState,
        String addressPostalCode,
        BigDecimal creditBalance,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
