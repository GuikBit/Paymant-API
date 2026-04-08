package br.com.holding.payments.company.dto;

import br.com.holding.payments.company.CompanyStatus;
import br.com.holding.payments.company.DowngradeValidationStrategy;
import br.com.holding.payments.company.PlanChangePolicy;

public record UpdateCompanyRequest(
        String razaoSocial,
        String nomeFantasia,
        String email,
        String phone,
        CompanyStatus status,
        PlanChangePolicy planChangePolicy,
        DowngradeValidationStrategy downgradeValidationStrategy,
        Integer gracePeriodDays
) {}
