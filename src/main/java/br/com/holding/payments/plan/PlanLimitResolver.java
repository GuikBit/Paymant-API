package br.com.holding.payments.plan;

import br.com.holding.payments.common.errors.ResourceNotFoundException;
import br.com.holding.payments.plan.dto.PlanLimitCheckResponse;
import br.com.holding.payments.plan.dto.PlanLimitDto;
import br.com.holding.payments.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Facade de consumo dos limites de plano em tempo de execucao.
 * Usado pelos modulos do sistema para decidir se podem executar uma acao
 * de acordo com o plano vigente.
 *
 * Exemplo:
 *   if (!resolver.isAllowed(planId, "relatorios_avancados")) { throw ... }
 *   Long max = resolver.getNumber(planId, "funcionarios").orElse(0L);
 */
@Service
@RequiredArgsConstructor
public class PlanLimitResolver {

    private final PlanRepository planRepository;
    private final PlanLimitCodec codec;

    @Transactional(readOnly = true)
    public List<PlanLimitDto> listLimits(Long planId) {
        return codec.deserialize(loadPlan(planId).getLimits());
    }

    @Transactional(readOnly = true)
    public List<PlanLimitDto> listFeatures(Long planId) {
        return codec.deserialize(loadPlan(planId).getFeatures());
    }

    @Transactional(readOnly = true)
    public Optional<PlanLimitDto> findLimit(Long planId, String key) {
        return listLimits(planId).stream().filter(l -> l.key().equals(key)).findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<Long> getNumber(Long planId, String key) {
        return findLimit(planId, key)
                .filter(l -> l.type() == PlanLimitType.NUMBER)
                .map(PlanLimitDto::value);
    }

    /**
     * Retorna true se o limite:
     *  - eh UNLIMITED, ou
     *  - eh BOOLEAN com enabled=true, ou
     *  - eh NUMBER com value > 0
     * Retorna false se nao existe ou esta desligado.
     */
    @Transactional(readOnly = true)
    public boolean isAllowed(Long planId, String key) {
        return findLimit(planId, key).map(this::allows).orElse(false);
    }

    /**
     * Verifica se uma operacao que elevaria o uso para `intendedUsage` e permitida.
     * Para BOOLEAN: respeita enabled. Para NUMBER: checa intendedUsage <= value.
     * Para UNLIMITED: sempre permite.
     */
    @Transactional(readOnly = true)
    public PlanLimitCheckResponse check(Long planId, String key, Integer intendedUsage) {
        Optional<PlanLimitDto> maybe = findLimit(planId, key);
        if (maybe.isEmpty()) {
            return PlanLimitCheckResponse.notFound(key);
        }
        PlanLimitDto l = maybe.get();
        boolean unlimited = l.type() == PlanLimitType.UNLIMITED;
        boolean allowed = switch (l.type()) {
            case UNLIMITED -> true;
            case BOOLEAN -> Boolean.TRUE.equals(l.enabled());
            case NUMBER -> l.value() != null && (intendedUsage == null || intendedUsage <= l.value());
        };
        return new PlanLimitCheckResponse(
                l.key(), l.label(), l.type(), l.value(), l.enabled(),
                unlimited, true, intendedUsage, allowed);
    }

    private boolean allows(PlanLimitDto l) {
        return switch (l.type()) {
            case UNLIMITED -> true;
            case BOOLEAN -> Boolean.TRUE.equals(l.enabled());
            case NUMBER -> l.value() != null && l.value() > 0;
        };
    }

    private Plan loadPlan(Long planId) {
        Long companyId = TenantContext.getRequiredCompanyId();
        return planRepository.findById(planId)
                .filter(p -> p.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Plan", planId));
    }
}
