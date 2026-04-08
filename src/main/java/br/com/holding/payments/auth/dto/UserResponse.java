package br.com.holding.payments.auth.dto;

import br.com.holding.payments.auth.Role;

import java.time.LocalDateTime;
import java.util.Set;

public record UserResponse(
        Long id,
        Long companyId,
        String email,
        String name,
        Set<Role> roles,
        Boolean active,
        LocalDateTime createdAt
) {}
