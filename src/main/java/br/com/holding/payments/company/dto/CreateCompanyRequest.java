package br.com.holding.payments.company.dto;

import br.com.holding.payments.company.AsaasEnvironment;
import br.com.holding.payments.company.DowngradeValidationStrategy;
import br.com.holding.payments.company.PlanChangePolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCompanyRequest(
        @NotBlank @Size(min = 14, max = 18) String cnpj,
        @NotBlank String razaoSocial,
        String nomeFantasia,
        String email,
        String phone,
        String asaasApiKey,
        AsaasEnvironment asaasEnv,
        PlanChangePolicy planChangePolicy,
        DowngradeValidationStrategy downgradeValidationStrategy,
        Integer gracePeriodDays
) {}
