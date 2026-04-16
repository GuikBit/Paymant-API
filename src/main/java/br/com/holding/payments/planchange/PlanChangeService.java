package br.com.holding.payments.planchange;

import br.com.holding.payments.audit.Auditable;
import br.com.holding.payments.charge.*;
import br.com.holding.payments.charge.dto.ChargeResponse;
import br.com.holding.payments.charge.dto.CreateChargeRequest;
import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.common.errors.ResourceNotFoundException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.company.DowngradeValidationStrategy;
import br.com.holding.payments.creditledger.CreditLedgerOrigin;
import br.com.holding.payments.creditledger.CustomerCreditLedger;
import br.com.holding.payments.creditledger.CustomerCreditLedgerService;
import br.com.holding.payments.customer.Customer;
import br.com.holding.payments.integration.asaas.gateway.AsaasGatewayService;
import br.com.holding.payments.integration.asaas.gateway.AsaasPaymentData;
import br.com.holding.payments.integration.asaas.gateway.AsaasSubscriptionData;
import br.com.holding.payments.integration.asaas.gateway.AsaasSubscriptionResult;
import br.com.holding.payments.outbox.OutboxPublisher;
import br.com.holding.payments.plan.Plan;
import br.com.holding.payments.plan.PlanCycle;
import br.com.holding.payments.plan.PlanRepository;
import br.com.holding.payments.plan.PlanService;
import br.com.holding.payments.planchange.dto.PlanChangePreviewResponse;
import br.com.holding.payments.planchange.dto.PlanChangeResponse;
import br.com.holding.payments.planchange.dto.RequestPlanChangeRequest;
import br.com.holding.payments.subscription.Subscription;
import br.com.holding.payments.subscription.SubscriptionRepository;
import br.com.holding.payments.subscription.SubscriptionStatus;
import br.com.holding.payments.subscription.dto.CreateSubscriptionRequest;
import br.com.holding.payments.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanChangeService {

    private final SubscriptionPlanChangeRepository planChangeRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final CompanyRepository companyRepository;
    private final CustomerCreditLedgerService creditLedgerService;
    private final ChargeService chargeService;
    private final PlanLimitsValidator planLimitsValidator;
    private final OutboxPublisher outboxPublisher;
    private final PlanChangeMapper planChangeMapper;
    private final PlanService planService;
    private final AsaasGatewayService asaasGateway;

    @Transactional(readOnly = true)
    public PlanChangePreviewResponse previewChange(Long subscriptionId, Long newPlanId) {
        Subscription subscription = getSubscriptionOrThrow(subscriptionId);
        Plan currentPlan = subscription.getPlan();
        Plan newPlan = getPlanOrThrow(newPlanId);

        validateBasicPreConditions(subscription, newPlan);

        PlanCycle currentCycle = subscription.getCycle();
        BigDecimal currentEffectivePrice = subscription.getEffectivePrice();
        BigDecimal newEffectivePrice = planService.getEffectivePrice(newPlan, currentCycle);

        Company company = subscription.getCompany();
        PlanChangePolicy policy = mapPolicy(company.getPlanChangePolicy());

        boolean fromFreeToPaid = Boolean.TRUE.equals(currentPlan.getIsFree()) && !Boolean.TRUE.equals(newPlan.getIsFree());
        boolean fromPaidToFree = !Boolean.TRUE.equals(currentPlan.getIsFree()) && Boolean.TRUE.equals(newPlan.getIsFree());

        // Free -> Paid: cria assinatura no Asaas agora (sem prorata, sem trial).
        // A primeira cobranca ja nasce do Asaas. Delta = novo valor cheio (UPGRADE).
        if (fromFreeToPaid) {
            return new PlanChangePreviewResponse(
                    subscriptionId,
                    currentPlan.getId(), currentPlan.getName(), currentEffectivePrice,
                    newPlan.getId(), newPlan.getName(), newEffectivePrice,
                    PlanChangeType.UPGRADE,
                    PlanChangePolicy.IMMEDIATE_NO_PRORATA,
                    newEffectivePrice,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );
        }

        // Paid -> Free: aguarda fim do ciclo (o cliente ja pagou o periodo corrente).
        // Sem credito pro-rata: o downgrade e respeitado so ao fim do periodo ja pago.
        if (fromPaidToFree) {
            return new PlanChangePreviewResponse(
                    subscriptionId,
                    currentPlan.getId(), currentPlan.getName(), currentEffectivePrice,
                    newPlan.getId(), newPlan.getName(), newEffectivePrice,
                    PlanChangeType.DOWNGRADE,
                    PlanChangePolicy.END_OF_CYCLE,
                    newEffectivePrice.subtract(currentEffectivePrice),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );
        }

        LocalDate periodStart = subscription.getCurrentPeriodStart() != null
                ? subscription.getCurrentPeriodStart().toLocalDate() : subscription.getCreatedAt().toLocalDate();
        LocalDate periodEnd = subscription.getNextDueDate() != null
                ? subscription.getNextDueDate() : periodStart.plusMonths(1);
        LocalDate changeDate = LocalDate.now();

        if (changeDate.isBefore(periodStart)) changeDate = periodStart;
        if (changeDate.isAfter(periodEnd)) changeDate = periodEnd;

        ProrationCalculator.ProrationResult proration;
        if (policy == PlanChangePolicy.END_OF_CYCLE || policy == PlanChangePolicy.IMMEDIATE_NO_PRORATA) {
            BigDecimal delta = newEffectivePrice.subtract(currentEffectivePrice);
            PlanChangeType type = delta.signum() > 0 ? PlanChangeType.UPGRADE
                    : delta.signum() < 0 ? PlanChangeType.DOWNGRADE : PlanChangeType.SIDEGRADE;
            proration = new ProrationCalculator.ProrationResult(delta, BigDecimal.ZERO, BigDecimal.ZERO, type);
        } else {
            proration = ProrationCalculator.calculate(
                    currentEffectivePrice, newEffectivePrice, periodStart, periodEnd, changeDate);
        }

        return new PlanChangePreviewResponse(
                subscriptionId,
                currentPlan.getId(), currentPlan.getName(), currentEffectivePrice,
                newPlan.getId(), newPlan.getName(), newEffectivePrice,
                proration.changeType(),
                policy,
                proration.delta(),
                proration.prorationCredit(),
                proration.prorationCharge()
        );
    }

    @Transactional
    @Auditable(action = "PLAN_CHANGE_REQUEST", entity = "SubscriptionPlanChange")
    public PlanChangeResponse requestChange(Long subscriptionId, RequestPlanChangeRequest request) {
        Subscription subscription = getSubscriptionOrThrow(subscriptionId);
        Plan currentPlan = subscription.getPlan();
        Plan newPlan = getPlanOrThrow(request.newPlanId());

        validateBasicPreConditions(subscription, newPlan);

        PlanCycle currentCycle = subscription.getCycle();
        BigDecimal currentEffectivePrice = subscription.getEffectivePrice();
        BigDecimal newEffectivePrice = planService.getEffectivePrice(newPlan, currentCycle);

        // Check no pending change exists
        planChangeRepository.findPendingBySubscriptionId(subscriptionId)
                .ifPresent(existing -> {
                    throw new BusinessException(
                            "Ja existe uma mudanca de plano pendente (id=" + existing.getId() + "). Cancele-a primeiro.");
                });

        Company company = subscription.getCompany();
        Long companyId = TenantContext.getRequiredCompanyId();

        boolean fromFreeToPaid = Boolean.TRUE.equals(currentPlan.getIsFree()) && !Boolean.TRUE.equals(newPlan.getIsFree());
        boolean fromPaidToFree = !Boolean.TRUE.equals(currentPlan.getIsFree()) && Boolean.TRUE.equals(newPlan.getIsFree());

        // ============================================================
        // CASO ESPECIAL: Plano gratuito -> plano pago
        // Cria a subscription no Asaas agora com nextDueDate = D+1,
        // sem trial, sem prorata, sem charge avulso.
        // ============================================================
        if (fromFreeToPaid) {
            validatePromoteFromFreeInputs(request);

            SubscriptionPlanChange planChange = SubscriptionPlanChange.builder()
                    .company(companyRepository.getReferenceById(companyId))
                    .subscription(subscription)
                    .previousPlan(currentPlan)
                    .requestedPlan(newPlan)
                    .previousCycle(currentCycle)
                    .requestedCycle(currentCycle)
                    .changeType(PlanChangeType.UPGRADE)
                    .policy(PlanChangePolicy.IMMEDIATE_NO_PRORATA)
                    .deltaAmount(newEffectivePrice)
                    .prorationCredit(BigDecimal.ZERO)
                    .prorationCharge(BigDecimal.ZERO)
                    .requestedBy(request.requestedBy())
                    .build();

            planChange = planChangeRepository.save(planChange);
            promoteFromFree(planChange, subscription, newPlan, currentCycle, newEffectivePrice, request, companyId);
            return planChangeMapper.toResponse(planChange);
        }

        // ============================================================
        // CASO ESPECIAL: Plano pago -> plano gratuito
        // Sempre END_OF_CYCLE: cliente usufrui o periodo ja pago, e no
        // fim do ciclo cancelamos a subscription no Asaas e trocamos
        // para o plano free. Historico do cliente no Asaas e preservado.
        // ============================================================
        if (fromPaidToFree) {
            LocalDate endOfCycle = subscription.getNextDueDate() != null
                    ? subscription.getNextDueDate()
                    : LocalDate.now().plusMonths(1);

            SubscriptionPlanChange planChange = SubscriptionPlanChange.builder()
                    .company(companyRepository.getReferenceById(companyId))
                    .subscription(subscription)
                    .previousPlan(currentPlan)
                    .requestedPlan(newPlan)
                    .previousCycle(currentCycle)
                    .requestedCycle(currentCycle)
                    .changeType(PlanChangeType.DOWNGRADE)
                    .policy(PlanChangePolicy.END_OF_CYCLE)
                    .deltaAmount(newEffectivePrice.subtract(currentEffectivePrice))
                    .prorationCredit(BigDecimal.ZERO)
                    .prorationCharge(BigDecimal.ZERO)
                    .requestedBy(request.requestedBy())
                    .build();
            planChange.transitionTo(PlanChangeStatus.SCHEDULED);
            planChange.setScheduledFor(endOfCycle.atStartOfDay());
            planChange = planChangeRepository.save(planChange);

            outboxPublisher.publish("PlanChangeScheduledEvent", "SubscriptionPlanChange",
                    planChange.getId().toString(), planChangeMapper.toResponse(planChange));

            log.info("Plan change (paid->free) scheduled: id={}, subscriptionId={}, scheduledFor={}",
                    planChange.getId(), subscriptionId, endOfCycle);
            return planChangeMapper.toResponse(planChange);
        }

        // ============================================================
        // Fluxo padrao: troca entre planos pagos
        // ============================================================
        PlanChangePolicy policy = mapPolicy(company.getPlanChangePolicy());

        // Calculate proration
        LocalDate periodStart = subscription.getCurrentPeriodStart() != null
                ? subscription.getCurrentPeriodStart().toLocalDate() : subscription.getCreatedAt().toLocalDate();
        LocalDate periodEnd = subscription.getNextDueDate() != null
                ? subscription.getNextDueDate() : periodStart.plusMonths(1);
        LocalDate changeDate = LocalDate.now();
        if (changeDate.isBefore(periodStart)) changeDate = periodStart;
        if (changeDate.isAfter(periodEnd)) changeDate = periodEnd;

        ProrationCalculator.ProrationResult proration;
        if (policy == PlanChangePolicy.END_OF_CYCLE || policy == PlanChangePolicy.IMMEDIATE_NO_PRORATA) {
            BigDecimal delta = newEffectivePrice.subtract(currentEffectivePrice);
            PlanChangeType type = delta.signum() > 0 ? PlanChangeType.UPGRADE
                    : delta.signum() < 0 ? PlanChangeType.DOWNGRADE : PlanChangeType.SIDEGRADE;
            proration = new ProrationCalculator.ProrationResult(delta, BigDecimal.ZERO, BigDecimal.ZERO, type);
        } else {
            proration = ProrationCalculator.calculate(
                    currentEffectivePrice, newEffectivePrice, periodStart, periodEnd, changeDate);
        }

        // Validate downgrade limits
        if (proration.changeType() == PlanChangeType.DOWNGRADE) {
            Map<String, Integer> currentUsage = request.currentUsage() != null
                    ? request.currentUsage() : Collections.emptyMap();
            PlanLimitsValidator.ValidationResult validation = planLimitsValidator.validate(
                    newPlan, currentUsage, company.getDowngradeValidationStrategy());

            if (!validation.allowed() && !validation.gracePeriod()) {
                if (validation.shouldSchedule()) {
                    policy = PlanChangePolicy.END_OF_CYCLE;
                } else {
                    throw new BusinessException("Downgrade bloqueado: " + String.join("; ", validation.violations()));
                }
            }
        }

        SubscriptionPlanChange planChange = SubscriptionPlanChange.builder()
                .company(companyRepository.getReferenceById(companyId))
                .subscription(subscription)
                .previousPlan(currentPlan)
                .requestedPlan(newPlan)
                .previousCycle(currentCycle)
                .requestedCycle(currentCycle)
                .changeType(proration.changeType())
                .policy(policy)
                .deltaAmount(proration.delta())
                .prorationCredit(proration.prorationCredit())
                .prorationCharge(proration.prorationCharge())
                .requestedBy(request.requestedBy())
                .build();

        // Handle based on policy
        if (policy == PlanChangePolicy.END_OF_CYCLE) {
            planChange.transitionTo(PlanChangeStatus.SCHEDULED);
            planChange.setScheduledFor(periodEnd.atStartOfDay());
            planChange = planChangeRepository.save(planChange);

            outboxPublisher.publish("PlanChangeScheduledEvent", "SubscriptionPlanChange",
                    planChange.getId().toString(), planChangeMapper.toResponse(planChange));

            log.info("Plan change scheduled: id={}, subscriptionId={}, scheduledFor={}",
                    planChange.getId(), subscriptionId, periodEnd);
        } else {
            planChange = planChangeRepository.save(planChange);
            applyPlanChange(planChange, subscription, currentPlan, newPlan);
        }

        return planChangeMapper.toResponse(planChange);
    }

    @Transactional
    public void confirmAfterPayment(Long chargeId) {
        SubscriptionPlanChange planChange = planChangeRepository.findByChargeId(chargeId)
                .orElse(null);

        if (planChange == null || planChange.getStatus() != PlanChangeStatus.AWAITING_PAYMENT) {
            return;
        }

        Subscription subscription = planChange.getSubscription();
        Plan newPlan = planChange.getRequestedPlan();

        subscription.setPlan(newPlan);
        subscription.setEffectivePrice(planService.getEffectivePrice(newPlan, subscription.getCycle()));
        subscriptionRepository.save(subscription);

        planChange.markEffective();
        planChangeRepository.save(planChange);

        outboxPublisher.publish("PlanChangedEvent", "SubscriptionPlanChange",
                planChange.getId().toString(), planChangeMapper.toResponse(planChange));

        log.info("Plan change confirmed after payment: id={}, subscriptionId={}, newPlan={}",
                planChange.getId(), subscription.getId(), newPlan.getName());
    }

    @Transactional
    @Auditable(action = "PLAN_CHANGE_CANCEL", entity = "SubscriptionPlanChange")
    public PlanChangeResponse cancelChange(Long subscriptionId, Long changeId) {
        SubscriptionPlanChange planChange = planChangeRepository.findById(changeId)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionPlanChange", changeId));

        if (!planChange.getSubscription().getId().equals(subscriptionId)) {
            throw new BusinessException("Mudanca de plano nao pertence a assinatura informada.");
        }

        planChange.transitionTo(PlanChangeStatus.CANCELED);
        planChange = planChangeRepository.save(planChange);

        log.info("Plan change canceled: id={}, subscriptionId={}", changeId, subscriptionId);
        return planChangeMapper.toResponse(planChange);
    }

    @Transactional(readOnly = true)
    public Page<PlanChangeResponse> listChanges(Long subscriptionId, Pageable pageable) {
        return planChangeRepository.findBySubscriptionId(subscriptionId, pageable)
                .map(planChangeMapper::toResponse);
    }

    @Transactional
    public void processScheduledChanges() {
        var scheduled = planChangeRepository.findScheduledReadyToProcess(LocalDateTime.now());

        if (scheduled.isEmpty()) return;

        log.info("Processing {} scheduled plan changes", scheduled.size());

        for (SubscriptionPlanChange planChange : scheduled) {
            try {
                TenantContext.setCompanyId(planChange.getCompany().getId());
                Subscription subscription = planChange.getSubscription();
                Plan previousPlan = planChange.getPreviousPlan();
                Plan newPlan = planChange.getRequestedPlan();

                boolean toFree = Boolean.TRUE.equals(newPlan.getIsFree());
                boolean fromPaid = !Boolean.TRUE.equals(previousPlan.getIsFree());

                // Paid -> Free agendado: cancela subscription no Asaas agora.
                // Isso nao apaga o historico de payments do cliente, apenas encerra
                // a emissao de novas cobrancas recorrentes.
                if (toFree && fromPaid && subscription.getAsaasId() != null) {
                    try {
                        asaasGateway.cancelSubscription(planChange.getCompany().getId(), subscription.getAsaasId());
                    } catch (Exception e) {
                        log.error("Failed to cancel Asaas subscription for paid->free change id={}: {}",
                                planChange.getId(), e.getMessage(), e);
                        throw e;
                    }
                    subscription.setAsaasId(null);
                }

                subscription.setPlan(newPlan);
                subscription.setEffectivePrice(planService.getEffectivePrice(newPlan, subscription.getCycle()));
                subscriptionRepository.save(subscription);

                planChange.markEffective();
                planChangeRepository.save(planChange);

                outboxPublisher.publish("PlanChangedEvent", "SubscriptionPlanChange",
                        planChange.getId().toString(), planChangeMapper.toResponse(planChange));

                log.info("Scheduled plan change applied: id={}, subscription={}, previous={}, new={}, toFree={}",
                        planChange.getId(), subscription.getId(), previousPlan.getName(), newPlan.getName(), toFree);
            } catch (Exception e) {
                planChange.markFailed("Erro ao processar: " + e.getMessage());
                planChangeRepository.save(planChange);
                log.error("Failed to process scheduled plan change id={}: {}",
                        planChange.getId(), e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    // ==================== PRIVATE ====================

    private void validatePromoteFromFreeInputs(RequestPlanChangeRequest request) {
        if (request.billingType() == null) {
            throw new BusinessException(
                    "billingType e obrigatorio ao promover assinatura de plano gratuito para plano pago.");
        }
        if (request.billingType() == BillingType.CREDIT_CARD
                && request.creditCard() == null && request.creditCardToken() == null) {
            throw new BusinessException(
                    "Dados de cartao (creditCard ou creditCardToken) sao obrigatorios para billingType=CREDIT_CARD.");
        }
    }

    private void promoteFromFree(SubscriptionPlanChange planChange,
                                  Subscription subscription,
                                  Plan newPlan,
                                  PlanCycle cycle,
                                  BigDecimal newEffectivePrice,
                                  RequestPlanChangeRequest request,
                                  Long companyId) {
        Customer customer = subscription.getCustomer();
        if (customer.getAsaasId() == null) {
            throw new BusinessException(
                    "Cliente nao possui asaas_id. Sincronize com o Asaas antes de promover para plano pago.");
        }

        AsaasPaymentData.CreditCardData creditCard = null;
        AsaasPaymentData.CreditCardHolderData holderInfo = null;

        CreateSubscriptionRequest.CreditCardData cc = request.creditCard();
        if (cc != null) {
            creditCard = new AsaasPaymentData.CreditCardData(
                    cc.holderName(), cc.number(), cc.expiryMonth(), cc.expiryYear(), cc.ccv());
        }

        CreateSubscriptionRequest.CreditCardHolderData holder = request.creditCardHolderInfo();
        if (holder != null) {
            holderInfo = new AsaasPaymentData.CreditCardHolderData(
                    holder.name(), holder.email(), holder.cpfCnpj(),
                    holder.postalCode(), holder.addressNumber(), holder.phone());
        }

        // D+1, sem trial (requisito: promocao de free nao recebe 14 dias de graca)
        LocalDate nextDueDate = LocalDate.now().plusDays(1);

        AsaasSubscriptionData subscriptionData = new AsaasSubscriptionData(
                customer.getAsaasId(),
                request.billingType().name(),
                newEffectivePrice,
                nextDueDate.toString(),
                cycle.name(),
                newPlan.getName(),
                "plan_change_" + planChange.getId(),
                creditCard,
                holderInfo,
                request.creditCardToken(),
                request.remoteIp()
        );

        AsaasSubscriptionResult asaasResult = asaasGateway.createSubscription(companyId, subscriptionData);

        subscription.setAsaasId(asaasResult.asaasId());
        subscription.setBillingType(request.billingType());
        subscription.setPlan(newPlan);
        subscription.setEffectivePrice(newEffectivePrice);
        subscription.setNextDueDate(nextDueDate);
        subscription.setCurrentPeriodStart(nextDueDate.atStartOfDay());
        subscriptionRepository.save(subscription);

        planChange.markEffective();
        planChangeRepository.save(planChange);

        outboxPublisher.publish("PlanChangedEvent", "SubscriptionPlanChange",
                planChange.getId().toString(), planChangeMapper.toResponse(planChange));

        log.info("Plan change (free->paid) applied: id={}, subscription={}, asaasId={}, newPlan={}, value={}",
                planChange.getId(), subscription.getId(), asaasResult.asaasId(),
                newPlan.getName(), newEffectivePrice);
    }

    private void applyPlanChange(SubscriptionPlanChange planChange,
                                  Subscription subscription, Plan currentPlan, Plan newPlan) {
        BigDecimal delta = planChange.getDeltaAmount();

        if (delta.compareTo(BigDecimal.ZERO) > 0) {
            // Upgrade: create charge for delta amount
            ChargeResponse chargeResponse = chargeService.createUndefinedCharge(new CreateChargeRequest(
                    subscription.getCustomer().getId(),
                    delta,
                    LocalDate.now().plusDays(1),
                    "Cobranca pro-rata upgrade: " + currentPlan.getName() + " -> " + newPlan.getName(),
                    "plan_change_" + planChange.getId(),
                    ChargeOrigin.PLAN_CHANGE,
                    null, null, null, null, null, null, null
            ));

            Charge charge = new Charge();
            charge.setId(chargeResponse.id());
            planChange.setCharge(charge);

            if (subscription.getBillingType() == BillingType.CREDIT_CARD) {
                // Credit card: assume immediate success, apply immediately
                subscription.setPlan(newPlan);
                subscription.setEffectivePrice(planService.getEffectivePrice(newPlan, subscription.getCycle()));
                subscriptionRepository.save(subscription);
                planChange.markEffective();
            } else {
                // PIX/Boleto: wait for payment
                planChange.transitionTo(PlanChangeStatus.AWAITING_PAYMENT);
                outboxPublisher.publish("PlanChangePendingPaymentEvent", "SubscriptionPlanChange",
                        planChange.getId().toString(), planChangeMapper.toResponse(planChange));
            }
        } else if (delta.compareTo(BigDecimal.ZERO) < 0) {
            // Downgrade: add credit to customer
            CustomerCreditLedger ledgerEntry = creditLedgerService.addCredit(
                    subscription.getCustomer().getId(),
                    delta.abs(),
                    CreditLedgerOrigin.DOWNGRADE_PRORATA,
                    "plan_change_" + planChange.getId(),
                    "Credito pro-rata downgrade: " + currentPlan.getName() + " -> " + newPlan.getName(),
                    planChange.getRequestedBy()
            );
            planChange.setCreditLedgerEntry(ledgerEntry);

            subscription.setPlan(newPlan);
            subscription.setEffectivePrice(planService.getEffectivePrice(newPlan, subscription.getCycle()));
            subscriptionRepository.save(subscription);
            planChange.markEffective();
        } else {
            // Sidegrade: just swap plans
            subscription.setPlan(newPlan);
            subscription.setEffectivePrice(planService.getEffectivePrice(newPlan, subscription.getCycle()));
            subscriptionRepository.save(subscription);
            planChange.markEffective();
        }

        planChangeRepository.save(planChange);

        if (planChange.getStatus() == PlanChangeStatus.EFFECTIVE) {
            outboxPublisher.publish("PlanChangedEvent", "SubscriptionPlanChange",
                    planChange.getId().toString(), planChangeMapper.toResponse(planChange));
        }

        log.info("Plan change applied: id={}, type={}, delta={}, status={}",
                planChange.getId(), planChange.getChangeType(), delta, planChange.getStatus());
    }

    private void validateBasicPreConditions(Subscription subscription, Plan newPlan) {
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new BusinessException("Apenas assinaturas ativas podem mudar de plano. Status atual: " + subscription.getStatus());
        }
        if (!newPlan.getActive()) {
            throw new BusinessException("Plano destino esta inativo.");
        }
        if (subscription.getPlan().getId().equals(newPlan.getId())) {
            throw new BusinessException("Plano destino e o mesmo plano atual.");
        }
    }

    private PlanChangePolicy mapPolicy(br.com.holding.payments.company.PlanChangePolicy companyPolicy) {
        return switch (companyPolicy) {
            case IMMEDIATE_PRORATA -> PlanChangePolicy.IMMEDIATE_PRORATA;
            case END_OF_CYCLE -> PlanChangePolicy.END_OF_CYCLE;
            case IMMEDIATE_NO_PRORATA -> PlanChangePolicy.IMMEDIATE_NO_PRORATA;
        };
    }

    private Subscription getSubscriptionOrThrow(Long id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", id));
    }

    private Plan getPlanOrThrow(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", id));
    }
}
