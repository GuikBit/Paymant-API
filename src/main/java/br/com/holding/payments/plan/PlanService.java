package br.com.holding.payments.plan;

import br.com.holding.payments.audit.Auditable;
import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.common.errors.ResourceNotFoundException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.plan.dto.*;
import br.com.holding.payments.subscription.SubscriptionRepository;
import br.com.holding.payments.subscription.SubscriptionStatus;
import br.com.holding.payments.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanService {

    private final PlanRepository planRepository;
    private final CompanyRepository companyRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanMapper planMapper;
    private final PlanLimitCodec limitCodec;

    private static final BigDecimal ANNUAL_MARGIN_TOLERANCE = new BigDecimal("0.05");

    @Transactional
    @Auditable(action = "PLAN_CREATE", entity = "Plan")
    public PlanResponse create(CreatePlanRequest request) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.getReferenceById(companyId);

        if (planRepository.existsByCodigoAndCompanyId(request.codigo(), companyId)) {
            throw new BusinessException("Ja existe um plano com o codigo '" + request.codigo() + "' para esta empresa.");
        }

        boolean isFree = Boolean.TRUE.equals(request.isFree());
        validateFreePlanConsistency(
                isFree,
                request.precoMensal(),
                request.precoAnual(),
                request.promoMensalAtiva(),
                request.promoAnualAtiva(),
                request.trialDays(),
                request.setupFee()
        );

        if (!isFree) {
            validatePromoMensal(
                    request.promoMensalAtiva(),
                    request.promoMensalPreco(),
                    request.promoMensalInicio(),
                    request.promoMensalFim(),
                    request.precoMensal()
            );

            validatePromoAnual(
                    request.promoAnualAtiva(),
                    request.promoAnualPreco(),
                    request.promoAnualInicio(),
                    request.promoAnualFim(),
                    request.precoAnual()
            );

            validatePrecoAnualMargin(request.precoMensal(), request.precoAnual(), request.descontoPercentualAnual());
        }

        Plan plan = Plan.builder()
                .company(company)
                .name(request.name())
                .description(request.description())
                .codigo(request.codigo())
                .precoMensal(request.precoMensal())
                .precoAnual(request.precoAnual())
                .descontoPercentualAnual(request.descontoPercentualAnual())
                .promoMensalAtiva(request.promoMensalAtiva() != null ? request.promoMensalAtiva() : false)
                .promoMensalPreco(request.promoMensalPreco())
                .promoMensalTexto(request.promoMensalTexto())
                .promoMensalInicio(request.promoMensalInicio())
                .promoMensalFim(request.promoMensalFim())
                .promoAnualAtiva(request.promoAnualAtiva() != null ? request.promoAnualAtiva() : false)
                .promoAnualPreco(request.promoAnualPreco())
                .promoAnualTexto(request.promoAnualTexto())
                .promoAnualInicio(request.promoAnualInicio())
                .promoAnualFim(request.promoAnualFim())
                .trialDays(request.trialDays() != null ? request.trialDays() : 0)
                .setupFee(request.setupFee() != null ? request.setupFee() : BigDecimal.ZERO)
                .limits(limitCodec.serialize(request.limits()))
                .features(limitCodec.serialize(request.features()))
                .tierOrder(request.tierOrder() != null ? request.tierOrder() : 0)
                .isFree(isFree)
                .build();

        plan = planRepository.save(plan);
        log.info("Plan created: id={}, codigo={}, precoMensal={}, isFree={}",
                plan.getId(), plan.getCodigo(), plan.getPrecoMensal(), plan.getIsFree());
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

        if (request.isFree() != null && !request.isFree().equals(plan.getIsFree())) {
            throw new BusinessException(
                    "Nao e possivel alterar a flag isFree de um plano existente. " +
                    "Crie uma nova versao do plano (cloneForNewVersion) ou um novo plano.");
        }

        if (request.name() != null) plan.setName(request.name());
        if (request.description() != null) plan.setDescription(request.description());
        if (request.precoMensal() != null) plan.setPrecoMensal(request.precoMensal());
        if (request.precoAnual() != null) plan.setPrecoAnual(request.precoAnual());
        if (request.descontoPercentualAnual() != null) plan.setDescontoPercentualAnual(request.descontoPercentualAnual());
        if (request.promoMensalAtiva() != null) plan.setPromoMensalAtiva(request.promoMensalAtiva());
        if (request.promoMensalPreco() != null) plan.setPromoMensalPreco(request.promoMensalPreco());
        if (request.promoMensalTexto() != null) plan.setPromoMensalTexto(request.promoMensalTexto());
        if (request.promoMensalInicio() != null) plan.setPromoMensalInicio(request.promoMensalInicio());
        if (request.promoMensalFim() != null) plan.setPromoMensalFim(request.promoMensalFim());
        if (request.promoAnualAtiva() != null) plan.setPromoAnualAtiva(request.promoAnualAtiva());
        if (request.promoAnualPreco() != null) plan.setPromoAnualPreco(request.promoAnualPreco());
        if (request.promoAnualTexto() != null) plan.setPromoAnualTexto(request.promoAnualTexto());
        if (request.promoAnualInicio() != null) plan.setPromoAnualInicio(request.promoAnualInicio());
        if (request.promoAnualFim() != null) plan.setPromoAnualFim(request.promoAnualFim());
        if (request.trialDays() != null) plan.setTrialDays(request.trialDays());
        if (request.setupFee() != null) plan.setSetupFee(request.setupFee());
        if (request.limits() != null) plan.setLimits(limitCodec.serialize(request.limits()));
        if (request.features() != null) plan.setFeatures(limitCodec.serialize(request.features()));
        if (request.tierOrder() != null) plan.setTierOrder(request.tierOrder());

        if (Boolean.TRUE.equals(plan.getIsFree())) {
            validateFreePlanConsistency(
                    true,
                    plan.getPrecoMensal(),
                    plan.getPrecoAnual(),
                    plan.getPromoMensalAtiva(),
                    plan.getPromoAnualAtiva(),
                    plan.getTrialDays(),
                    plan.getSetupFee()
            );
        } else {
            // Re-validate promo mensal if any promo mensal field was updated
            Boolean promoMensalAtiva = plan.getPromoMensalAtiva();
            if (Boolean.TRUE.equals(promoMensalAtiva)) {
                validatePromoMensal(
                        promoMensalAtiva,
                        plan.getPromoMensalPreco(),
                        plan.getPromoMensalInicio(),
                        plan.getPromoMensalFim(),
                        plan.getPrecoMensal()
                );
            }

            // Re-validate promo anual if any promo anual field was updated
            Boolean promoAnualAtiva = plan.getPromoAnualAtiva();
            if (Boolean.TRUE.equals(promoAnualAtiva)) {
                validatePromoAnual(
                        promoAnualAtiva,
                        plan.getPromoAnualPreco(),
                        plan.getPromoAnualInicio(),
                        plan.getPromoAnualFim(),
                        plan.getPrecoAnual()
                );
            }

            // Re-validate annual price margin if pricing fields were updated
            if (request.precoMensal() != null || request.precoAnual() != null || request.descontoPercentualAnual() != null) {
                validatePrecoAnualMargin(plan.getPrecoMensal(), plan.getPrecoAnual(), plan.getDescontoPercentualAnual());
            }
        }

        plan = planRepository.save(plan);
        log.info("Plan updated: id={}, codigo={}", plan.getId(), plan.getCodigo());
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

        long activeCount = subscriptionRepository.countByPlanIdAndStatus(id, SubscriptionStatus.ACTIVE);
        if (activeCount > 0) {
            throw new BusinessException(
                    "Nao e possivel excluir plano com " + activeCount + " assinatura(s) ativa(s). Cancele as assinaturas primeiro.");
        }

        plan.softDelete();
        planRepository.save(plan);
        log.info("Plan soft-deleted: id={}", id);
    }

    /**
     * Cria uma nova versao do plano com preco alterado.
     * O plano antigo e desativado, o novo herda codigo, nome e configuracoes.
     */
    @Transactional
    @Auditable(action = "PLAN_NEW_VERSION", entity = "Plan")
    public PlanResponse cloneForNewVersion(Long id, UpdatePlanRequest request) {
        Plan original = getPlanOrThrow(id);
        Long companyId = TenantContext.getRequiredCompanyId();

        int nextVersion = planRepository.findMaxVersionByCompanyAndCodigo(companyId, original.getCodigo())
                .orElse(0) + 1;

        // Deactivate original
        original.setActive(false);
        planRepository.save(original);

        // Create new version
        Plan newPlan = Plan.builder()
                .company(original.getCompany())
                .codigo(original.getCodigo())
                .name(original.getName())
                .description(request.description() != null ? request.description() : original.getDescription())
                .precoMensal(request.precoMensal() != null ? request.precoMensal() : original.getPrecoMensal())
                .precoAnual(request.precoAnual() != null ? request.precoAnual() : original.getPrecoAnual())
                .descontoPercentualAnual(request.descontoPercentualAnual() != null ? request.descontoPercentualAnual() : original.getDescontoPercentualAnual())
                .promoMensalAtiva(request.promoMensalAtiva() != null ? request.promoMensalAtiva() : original.getPromoMensalAtiva())
                .promoMensalPreco(request.promoMensalPreco() != null ? request.promoMensalPreco() : original.getPromoMensalPreco())
                .promoMensalTexto(request.promoMensalTexto() != null ? request.promoMensalTexto() : original.getPromoMensalTexto())
                .promoMensalInicio(request.promoMensalInicio() != null ? request.promoMensalInicio() : original.getPromoMensalInicio())
                .promoMensalFim(request.promoMensalFim() != null ? request.promoMensalFim() : original.getPromoMensalFim())
                .promoAnualAtiva(request.promoAnualAtiva() != null ? request.promoAnualAtiva() : original.getPromoAnualAtiva())
                .promoAnualPreco(request.promoAnualPreco() != null ? request.promoAnualPreco() : original.getPromoAnualPreco())
                .promoAnualTexto(request.promoAnualTexto() != null ? request.promoAnualTexto() : original.getPromoAnualTexto())
                .promoAnualInicio(request.promoAnualInicio() != null ? request.promoAnualInicio() : original.getPromoAnualInicio())
                .promoAnualFim(request.promoAnualFim() != null ? request.promoAnualFim() : original.getPromoAnualFim())
                .trialDays(request.trialDays() != null ? request.trialDays() : original.getTrialDays())
                .setupFee(request.setupFee() != null ? request.setupFee() : original.getSetupFee())
                .limits(request.limits() != null ? limitCodec.serialize(request.limits()) : original.getLimits())
                .features(request.features() != null ? limitCodec.serialize(request.features()) : original.getFeatures())
                .tierOrder(request.tierOrder() != null ? request.tierOrder() : original.getTierOrder())
                .isFree(request.isFree() != null ? request.isFree() : original.getIsFree())
                .version(nextVersion)
                .build();

        newPlan = planRepository.save(newPlan);
        log.info("Plan new version created: id={}, codigo={}, version={}", newPlan.getId(), newPlan.getCodigo(), nextVersion);
        return planMapper.toResponse(newPlan);
    }

    /**
     * Calcula o preco efetivo de um plano para um determinado ciclo,
     * considerando promocoes ativas e descontos percentuais.
     */
    public BigDecimal getEffectivePrice(Plan plan, PlanCycle cycle) {
        if (Boolean.TRUE.equals(plan.getIsFree())) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        }

        LocalDateTime now = LocalDateTime.now();

        switch (cycle) {
            case MONTHLY: {
                if (isPromoActive(plan.getPromoMensalAtiva(), plan.getPromoMensalInicio(), plan.getPromoMensalFim())) {
                    return plan.getPromoMensalPreco().setScale(2, RoundingMode.HALF_EVEN);
                }
                return plan.getPrecoMensal().setScale(2, RoundingMode.HALF_EVEN);
            }
            case SEMIANNUALLY: {
                BigDecimal monthlyPrice;
                if (isPromoActive(plan.getPromoMensalAtiva(), plan.getPromoMensalInicio(), plan.getPromoMensalFim())) {
                    monthlyPrice = plan.getPromoMensalPreco();
                } else {
                    monthlyPrice = plan.getPrecoMensal();
                }
                return monthlyPrice.multiply(new BigDecimal("6")).setScale(2, RoundingMode.HALF_EVEN);
            }
            case YEARLY: {
                if (isPromoActive(plan.getPromoAnualAtiva(), plan.getPromoAnualInicio(), plan.getPromoAnualFim())) {
                    return plan.getPromoAnualPreco().setScale(2, RoundingMode.HALF_EVEN);
                }
                if (plan.getPrecoAnual() != null) {
                    return plan.getPrecoAnual().setScale(2, RoundingMode.HALF_EVEN);
                }
                BigDecimal annualFromMonthly = plan.getPrecoMensal().multiply(new BigDecimal("12"));
                if (plan.getDescontoPercentualAnual() != null) {
                    BigDecimal discountFactor = BigDecimal.ONE.subtract(
                            plan.getDescontoPercentualAnual().divide(new BigDecimal("100"), 10, RoundingMode.HALF_EVEN)
                    );
                    annualFromMonthly = annualFromMonthly.multiply(discountFactor);
                }
                return annualFromMonthly.setScale(2, RoundingMode.HALF_EVEN);
            }
            default:
                throw new BusinessException("Ciclo de plano nao suportado: " + cycle);
        }
    }

    @Transactional(readOnly = true)
    public PlanResponse findByCodigo(String codigo) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Plan plan = planRepository.findByCodigoAndCompanyIdAndActiveTrue(codigo, companyId)
                .orElseThrow(() -> new BusinessException("Plano com codigo '" + codigo + "' nao encontrado ou inativo."));
        return planMapper.toResponse(plan);
    }

    @Transactional(readOnly = true)
    public PlanPricingResponse getPricing(Long id) {
        Plan plan = getPlanOrThrow(id);

        BigDecimal effectiveMensal = getEffectivePrice(plan, PlanCycle.MONTHLY);
        BigDecimal effectiveSemestral = getEffectivePrice(plan, PlanCycle.SEMIANNUALLY);
        BigDecimal effectiveAnual = getEffectivePrice(plan, PlanCycle.YEARLY);

        PlanPricingResponse.PromoPricing promoMensal;
        if (isPromoActive(plan.getPromoMensalAtiva(), plan.getPromoMensalInicio(), plan.getPromoMensalFim())) {
            promoMensal = new PlanPricingResponse.PromoPricing(
                    true, plan.getPromoMensalPreco(), plan.getPromoMensalTexto(), plan.getPromoMensalFim());
        } else {
            promoMensal = new PlanPricingResponse.PromoPricing(false, null, null, null);
        }

        PlanPricingResponse.PromoPricing promoAnual;
        if (isPromoActive(plan.getPromoAnualAtiva(), plan.getPromoAnualInicio(), plan.getPromoAnualFim())) {
            promoAnual = new PlanPricingResponse.PromoPricing(
                    true, plan.getPromoAnualPreco(), plan.getPromoAnualTexto(), plan.getPromoAnualFim());
        } else {
            promoAnual = new PlanPricingResponse.PromoPricing(false, null, null, null);
        }

        return new PlanPricingResponse(
                plan.getId(),
                plan.getCodigo(),
                plan.getName(),
                effectiveMensal,
                effectiveSemestral,
                effectiveAnual,
                plan.getDescontoPercentualAnual(),
                promoMensal,
                promoAnual,
                limitCodec.deserialize(plan.getFeatures()),
                limitCodec.deserialize(plan.getLimits())
        );
    }

    // ---- Private helpers ----

    private boolean isPromoActive(Boolean ativa, LocalDateTime inicio, LocalDateTime fim) {
        if (!Boolean.TRUE.equals(ativa) || inicio == null || fim == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(inicio) && !now.isAfter(fim);
    }

    private void validateFreePlanConsistency(boolean isFree,
                                              BigDecimal precoMensal,
                                              BigDecimal precoAnual,
                                              Boolean promoMensalAtiva,
                                              Boolean promoAnualAtiva,
                                              Integer trialDays,
                                              BigDecimal setupFee) {
        if (!isFree) {
            return;
        }
        if (precoMensal != null && precoMensal.compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException("Plano gratuito deve ter precoMensal igual a 0.");
        }
        if (precoAnual != null && precoAnual.compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException("Plano gratuito deve ter precoAnual igual a 0 ou nulo.");
        }
        if (Boolean.TRUE.equals(promoMensalAtiva) || Boolean.TRUE.equals(promoAnualAtiva)) {
            throw new BusinessException("Plano gratuito nao pode ter promocoes ativas.");
        }
        if (trialDays != null && trialDays > 0) {
            throw new BusinessException("Plano gratuito nao pode ter trialDays > 0.");
        }
        if (setupFee != null && setupFee.compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException("Plano gratuito nao pode ter setupFee.");
        }
    }

    private void validatePromoMensal(Boolean ativa, BigDecimal preco, LocalDateTime inicio, LocalDateTime fim, BigDecimal precoMensal) {
        if (!Boolean.TRUE.equals(ativa)) {
            return;
        }
        if (preco == null) {
            throw new BusinessException("Preco promocional mensal e obrigatorio quando a promocao mensal esta ativa.");
        }
        if (inicio == null) {
            throw new BusinessException("Data de inicio da promocao mensal e obrigatoria quando a promocao mensal esta ativa.");
        }
        if (fim == null) {
            throw new BusinessException("Data de fim da promocao mensal e obrigatoria quando a promocao mensal esta ativa.");
        }
        if (preco.compareTo(precoMensal) >= 0) {
            throw new BusinessException("Preco promocional mensal (" + preco + ") deve ser menor que o preco mensal (" + precoMensal + ").");
        }
        if (!fim.isAfter(inicio)) {
            throw new BusinessException("Data de fim da promocao mensal deve ser posterior a data de inicio.");
        }
    }

    private void validatePromoAnual(Boolean ativa, BigDecimal preco, LocalDateTime inicio, LocalDateTime fim, BigDecimal precoAnual) {
        if (!Boolean.TRUE.equals(ativa)) {
            return;
        }
        if (precoAnual == null) {
            throw new BusinessException("Preco anual deve estar definido quando a promocao anual esta ativa.");
        }
        if (preco == null) {
            throw new BusinessException("Preco promocional anual e obrigatorio quando a promocao anual esta ativa.");
        }
        if (inicio == null) {
            throw new BusinessException("Data de inicio da promocao anual e obrigatoria quando a promocao anual esta ativa.");
        }
        if (fim == null) {
            throw new BusinessException("Data de fim da promocao anual e obrigatoria quando a promocao anual esta ativa.");
        }
        if (preco.compareTo(precoAnual) >= 0) {
            throw new BusinessException("Preco promocional anual (" + preco + ") deve ser menor que o preco anual (" + precoAnual + ").");
        }
        if (!fim.isAfter(inicio)) {
            throw new BusinessException("Data de fim da promocao anual deve ser posterior a data de inicio.");
        }
    }

    private void validatePrecoAnualMargin(BigDecimal precoMensal, BigDecimal precoAnual, BigDecimal descontoPercentualAnual) {
        if (precoMensal == null || precoAnual == null) {
            return;
        }

        BigDecimal expected = precoMensal.multiply(new BigDecimal("12"));
        if (descontoPercentualAnual != null) {
            BigDecimal discountFactor = BigDecimal.ONE.subtract(
                    descontoPercentualAnual.divide(new BigDecimal("100"), 10, RoundingMode.HALF_EVEN)
            );
            expected = expected.multiply(discountFactor).setScale(2, RoundingMode.HALF_EVEN);
        }

        BigDecimal diff = precoAnual.subtract(expected).abs();
        BigDecimal tolerance = expected.multiply(ANNUAL_MARGIN_TOLERANCE);

        if (diff.compareTo(tolerance) > 0) {
            throw new BusinessException(
                    "Preco anual (" + precoAnual + ") esta fora da margem de 5%% do valor esperado (" + expected.setScale(2, RoundingMode.HALF_EVEN)
                            + "). Diferenca: " + diff.setScale(2, RoundingMode.HALF_EVEN) + ", tolerancia maxima: " + tolerance.setScale(2, RoundingMode.HALF_EVEN) + "."
            );
        }
    }

    private Plan getPlanOrThrow(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", id));
    }
}
