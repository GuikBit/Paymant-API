package br.com.holding.payments.plan;

import br.com.holding.payments.audit.Auditable;
import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.common.errors.ResourceNotFoundException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.plan.dto.*;
import br.com.holding.payments.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanService {

    private final PlanRepository planRepository;
    private final CompanyRepository companyRepository;
    private final PlanMapper planMapper;

    @Transactional
    @Auditable(action = "PLAN_CREATE", entity = "Plan")
    public PlanResponse create(CreatePlanRequest request) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.getReferenceById(companyId);

        Plan plan = Plan.builder()
                .company(company)
                .name(request.name())
                .description(request.description())
                .value(request.value())
                .cycle(request.cycle())
                .trialDays(request.trialDays() != null ? request.trialDays() : 0)
                .setupFee(request.setupFee() != null ? request.setupFee() : java.math.BigDecimal.ZERO)
                .limits(request.limits())
                .features(request.features())
                .tierOrder(request.tierOrder() != null ? request.tierOrder() : 0)
                .build();

        plan = planRepository.save(plan);
        return planMapper.toResponse(plan);
    }

    @Transactional(readOnly = true)
    public Page<PlanResponse> findAll(Pageable pageable) {
        return planRepository.findAll(pageable).map(planMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PlanResponse findById(Long id) {
        return planMapper.toResponse(getPlanOrThrow(id));
    }

    @Transactional
    @Auditable(action = "PLAN_UPDATE", entity = "Plan")
    public PlanResponse update(Long id, UpdatePlanRequest request) {
        Plan plan = getPlanOrThrow(id);

        if (request.name() != null) plan.setName(request.name());
        if (request.description() != null) plan.setDescription(request.description());
        if (request.value() != null) plan.setValue(request.value());
        if (request.trialDays() != null) plan.setTrialDays(request.trialDays());
        if (request.setupFee() != null) plan.setSetupFee(request.setupFee());
        if (request.limits() != null) plan.setLimits(request.limits());
        if (request.features() != null) plan.setFeatures(request.features());
        if (request.tierOrder() != null) plan.setTierOrder(request.tierOrder());

        plan = planRepository.save(plan);
        return planMapper.toResponse(plan);
    }

    @Transactional
    @Auditable(action = "PLAN_ACTIVATE", entity = "Plan")
    public PlanResponse activate(Long id) {
        Plan plan = getPlanOrThrow(id);
        plan.setActive(true);
        plan = planRepository.save(plan);
        return planMapper.toResponse(plan);
    }

    @Transactional
    @Auditable(action = "PLAN_DEACTIVATE", entity = "Plan")
    public PlanResponse deactivate(Long id) {
        Plan plan = getPlanOrThrow(id);
        plan.setActive(false);
        plan = planRepository.save(plan);
        return planMapper.toResponse(plan);
    }

    @Transactional
    @Auditable(action = "PLAN_SOFT_DELETE", entity = "Plan")
    public void softDelete(Long id) {
        Plan plan = getPlanOrThrow(id);

        // TODO: Fase 7 - verificar se existem assinaturas ativas antes de deletar

        plan.softDelete();
        planRepository.save(plan);
        log.info("Plan soft-deleted: id={}", id);
    }

    /**
     * Cria uma nova versao do plano com preco alterado.
     * O plano antigo e desativado, o novo herda nome e configuracoes.
     */
    @Transactional
    @Auditable(action = "PLAN_NEW_VERSION", entity = "Plan")
    public PlanResponse cloneForNewVersion(Long id, UpdatePlanRequest request) {
        Plan original = getPlanOrThrow(id);
        Long companyId = TenantContext.getRequiredCompanyId();

        int nextVersion = planRepository.findMaxVersionByCompanyAndName(companyId, original.getName())
                .orElse(0) + 1;

        // Deactivate original
        original.setActive(false);
        planRepository.save(original);

        // Create new version
        Plan newPlan = Plan.builder()
                .company(original.getCompany())
                .name(original.getName())
                .description(request.description() != null ? request.description() : original.getDescription())
                .value(request.value() != null ? request.value() : original.getValue())
                .cycle(original.getCycle())
                .trialDays(request.trialDays() != null ? request.trialDays() : original.getTrialDays())
                .setupFee(request.setupFee() != null ? request.setupFee() : original.getSetupFee())
                .limits(request.limits() != null ? request.limits() : original.getLimits())
                .features(request.features() != null ? request.features() : original.getFeatures())
                .tierOrder(request.tierOrder() != null ? request.tierOrder() : original.getTierOrder())
                .version(nextVersion)
                .build();

        newPlan = planRepository.save(newPlan);
        log.info("Plan new version created: id={}, name={}, version={}", newPlan.getId(), newPlan.getName(), nextVersion);
        return planMapper.toResponse(newPlan);
    }

    private Plan getPlanOrThrow(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", id));
    }
}
