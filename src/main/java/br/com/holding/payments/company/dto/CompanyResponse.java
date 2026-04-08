package br.com.holding.payments.company.dto;

import br.com.holding.payments.company.AsaasEnvironment;
import br.com.holding.payments.company.CompanyStatus;
import br.com.holding.payments.company.DowngradeValidationStrategy;
import br.com.holding.payments.company.PlanChangePolicy;

import java.time.LocalDateTime;

public record CompanyResponse(
        Long id,
        String cnpj,
        String razaoSocial,
        String nomeFantasia,
        String email,
        String phone,
        AsaasEnvironment asaasEnv,
        boolean hasAsaasKey,
        CompanyStatus status,
        PlanChangePolicy planChangePolicy,
        DowngradeValidationStrategy downgradeValidationStrategy,
        Integer gracePeriodDays,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
