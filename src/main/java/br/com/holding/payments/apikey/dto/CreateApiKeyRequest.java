package br.com.holding.payments.apikey.dto;

import br.com.holding.payments.auth.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Set;

public record CreateApiKeyRequest(
        @NotBlank String name,
        String description,
        @NotNull Set<Role> roles,
        LocalDateTime expiresAt
) {}
