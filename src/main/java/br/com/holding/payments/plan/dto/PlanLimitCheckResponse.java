package br.com.holding.payments.plan.dto;

import br.com.holding.payments.plan.PlanLimitType;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Resposta de consulta de um limite especifico de plano em tempo de execucao.
 * Usada para o sistema cliente decidir se pode ou nao executar uma acao.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanLimitCheckResponse(
        String key,
        String label,
        PlanLimitType type,
        Long value,
        Boolean enabled,
        boolean unlimited,
        boolean found,
        Integer currentUsage,
        Boolean allowed
) {
    public static PlanLimitCheckResponse notFound(String key) {
        return new PlanLimitCheckResponse(key, null, null, null, null, false, false, null, null);
    }
}
