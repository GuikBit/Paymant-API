package br.com.holding.payments.company.dto;

import br.com.holding.payments.company.AsaasEnvironment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateCredentialsRequest(
        @NotBlank String asaasApiKey,
        @NotNull AsaasEnvironment asaasEnv
) {}
