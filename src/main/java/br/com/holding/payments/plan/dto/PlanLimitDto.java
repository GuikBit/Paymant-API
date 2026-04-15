package br.com.holding.payments.plan.dto;

import br.com.holding.payments.plan.PlanLimitType;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanLimitDto(
        @NotBlank(message = "key do limite e obrigatorio")
        @Pattern(regexp = "^[a-z][a-z0-9_]*$",
                message = "key deve conter apenas letras minusculas, numeros e underline, iniciando com letra")
        String key,

        @NotBlank(message = "label do limite e obrigatorio")
        String label,

        @NotNull(message = "type do limite e obrigatorio (NUMBER, BOOLEAN ou UNLIMITED)")
        PlanLimitType type,

        @PositiveOrZero(message = "value nao pode ser negativo")
        Long value,

        Boolean enabled
) {}
