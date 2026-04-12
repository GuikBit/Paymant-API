package br.com.holding.payments.apikey.dto;

import br.com.holding.payments.auth.Role;

import java.time.LocalDateTime;
import java.util.Set;

public record ApiKeyResponse(
        Long id,
        Long companyId,
        String keyPrefix,
        String name,
        String description,
        Set<Role> roles,
        Boolean active,
        LocalDateTime lastUsedAt,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {}
