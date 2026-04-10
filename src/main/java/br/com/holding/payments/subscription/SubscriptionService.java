package br.com.holding.payments.subscription;

import br.com.holding.payments.audit.Auditable;
import br.com.holding.payments.charge.*;
import br.com.holding.payments.charge.dto.ChargeResponse;
import br.com.holding.payments.charge.ChargeMapper;
import br.com.holding.payments.coupon.*;
import br.com.holding.payments.integration.asaas.dto.AsaasPageResponse;
import br.com.holding.payments.integration.asaas.dto.AsaasPaymentResponse;
import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.common.errors.ResourceNotFoundException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.customer.Customer;
import br.com.holding.payments.customer.CustomerRepository;
import br.com.holding.payments.integration.asaas.gateway.*;
import br.com.holding.payments.outbox.OutboxPublisher;
import br.com.holding.payments.plan.Plan;
import br.com.holding.payments.plan.PlanCycle;
import br.com.holding.payments.plan.PlanRepository;
import br.com.holding.payments.plan.PlanService;
import br.com.holding.payments.subscription.dto.*;
import br.com.holding.payments.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final CustomerRepository customerRepository;
    private final CompanyRepository companyRepository;
    private final PlanRepository planRepository;
    private final PlanService planService;
    private final ChargeRepository chargeRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final ChargeMapper chargeMapper;
    private final AsaasGatewayService asaasGateway;
    private final OutboxPublisher outboxPublisher;
    private final CouponService couponService;

    @Transactional
    @Auditable(action = "SUBSCRIPTION_CREATE", entity = "Subscription")
    public SubscriptionResponse subscribe(CreateSubscriptionRequest request) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.getReferenceById(companyId);

        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

        if (customer.getAsaasId() == null) {
            throw new BusinessException("Cliente nao possui asaas_id. Sincronize com o Asaas primeiro.");
        }

        Plan plan = planRepository.findById(request.planId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan", request.planId()));

        if (!plan.getActive()) {
            throw new BusinessException("Plano inativo nao pode ser utilizado para novas assinaturas.");
        }

        PlanCycle cycle = request.cycle();
        BigDecimal effectivePrice = planService.getEffectivePrice(plan, cycle);

        // Coupon handling
        BigDecimal valueForAsaas = effectivePrice;
        Long appliedCouponId = null;
        String appliedCouponCode = null;
        BigDecimal appliedDiscountAmount = null;
        Integer couponUsesRemaining = null;

        CouponCalculator.CouponDiscountResult couponResult = null;
        Coupon coupon = null;

        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            String upperCode = request.couponCode().toUpperCase();

            // Lookup coupon entity to get its ID and details
            coupon = couponService.findActiveEntityByCode(upperCode);

            // Validate and calculate discount (usage is recorded AFTER subscription is saved)
            couponResult = couponService.validateAndCalculateForSubscription(
                    coupon.getId(),
                    customer.getId(),
                    plan.getCodigo(),
                    cycle.name(),
                    effectivePrice
            );

            valueForAsaas = couponResult.finalValue();
            appliedDiscountAmount = couponResult.discountAmount();
            appliedCouponId = coupon.getId();
            appliedCouponCode = coupon.getCode();

            if (coupon.getApplicationType() == CouponApplicationType.FIRST_CHARGE) {
                couponUsesRemaining = 1;
            } else if (coupon.getRecurrenceMonths() != null) {
                couponUsesRemaining = coupon.getRecurrenceMonths();
            }
            // null = permanent discount
        }

        // Build Asaas subscription data
        AsaasPaymentData.CreditCardData creditCard = null;
        AsaasPaymentData.CreditCardHolderData holderInfo = null;

        if (request.creditCard() != null) {
            creditCard = new AsaasPaymentData.CreditCardData(
                    request.creditCard().holderName(),
                    request.creditCard().number(),
                    request.creditCard().expiryMonth(),
                    request.creditCard().expiryYear(),
                    request.creditCard().ccv()
            );
        }

        if (request.creditCardHolderInfo() != null) {
            holderInfo = new AsaasPaymentData.CreditCardHolderData(
                    request.creditCardHolderInfo().name(),
                    request.creditCardHolderInfo().email(),
                    request.creditCardHolderInfo().cpfCnpj(),
                    request.creditCardHolderInfo().postalCode(),
                    request.creditCardHolderInfo().addressNumber(),
                    request.creditCardHolderInfo().phone()
            );
        }

        LocalDate nextDueDate = request.nextDueDate() != null ? request.nextDueDate() : LocalDate.now().plusDays(1);

        AsaasSubscriptionData subscriptionData = new AsaasSubscriptionData(
                customer.getAsaasId(),
                request.billingType().name(),
                valueForAsaas,
                nextDueDate.toString(),
                cycle.name(),
                request.description() != null ? request.description() : plan.getName(),
                request.externalReference(),
                creditCard,
                holderInfo,
                request.creditCardToken(),
                request.remoteIp()
        );

        AsaasSubscriptionResult asaasResult = asaasGateway.createSubscription(companyId, subscriptionData);

        Subscription subscription = Subscription.builder()
                .company(company)
                .customer(customer)
                .plan(plan)
                .asaasId(asaasResult.asaasId())
                .billingType(request.billingType())
                .cycle(cycle)
                .effectivePrice(effectivePrice)
                .couponId(appliedCouponId)
                .couponCode(appliedCouponCode)
                .couponDiscountAmount(appliedDiscountAmount)
                .couponUsesRemaining(couponUsesRemaining)
                .nextDueDate(nextDueDate)
                .currentPeriodStart(nextDueDate.atStartOfDay())
                .build();

        subscription = subscriptionRepository.save(subscription);

        // Record coupon usage now that subscription ID is available
        if (coupon != null && couponResult != null) {
            couponService.recordSubscriptionUsage(
                    coupon.getId(), customer.getId(), subscription.getId(),
                    plan.getCodigo(), cycle.name(),
                    effectivePrice, couponResult);
        }

        // Sync charges generated by Asaas for this subscription
        syncSubscriptionCharges(companyId, subscription, company, customer);

        outboxPublisher.publish("SubscriptionCreatedEvent", "Subscription",
                subscription.getId().toString(), subscriptionMapper.toResponse(subscription));

        log.info("Subscription created: id={}, asaasId={}, plan={}, customer={}",
                subscription.getId(), subscription.getAsaasId(), plan.getName(), customer.getId());

        return subscriptionMapper.toResponse(subscription);
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionResponse> findAll(SubscriptionStatus status, Long customerId, Pageable pageable) {
        return subscriptionRepository.findWithFilters(status, customerId, pageable)
                .map(subscriptionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse findById(Long id) {
        return subscriptionMapper.toResponse(getSubscriptionOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<ChargeResponse> listCharges(Long subscriptionId, Pageable pageable) {
        getSubscriptionOrThrow(subscriptionId); // validate existence
        return chargeRepository.findBySubscriptionId(subscriptionId, pageable)
                .map(chargeMapper::toResponse);
    }

    @Transactional
    @Auditable(action = "SUBSCRIPTION_UPDATE", entity = "Subscription")
    public SubscriptionResponse update(Long id, UpdateSubscriptionRequest request) {
        Subscription subscription = getSubscriptionOrThrow(id);

        if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
            throw new BusinessException("Nao e possivel atualizar uma assinatura cancelada.");
        }

        if (request.billingType() != null) {
            subscription.setBillingType(request.billingType());
        }
        if (request.nextDueDate() != null) {
            subscription.setNextDueDate(request.nextDueDate());
        }

        subscription = subscriptionRepository.save(subscription);
        log.info("Subscription updated: id={}", id);
        return subscriptionMapper.toResponse(subscription);
    }

    @Transactional
    @Auditable(action = "SUBSCRIPTION_CANCEL", entity = "Subscription")
    public SubscriptionResponse cancel(Long id, boolean confirmCouponRemoval) {
        Subscription subscription = getSubscriptionOrThrow(id);

        if (subscription.getCouponId() != null && !confirmCouponRemoval) {
            throw new BusinessException(
                    "A assinatura possui o cupom '" + subscription.getCouponCode() + "' aplicado. " +
                    "Ao cancelar, o cupom sera removido permanentemente. " +
                    "Envie confirmCouponRemoval=true para confirmar.");
        }

        if (subscription.getCouponId() != null) {
            couponService.removeCouponFromSubscription(subscription, "CANCELED");
        }

        subscription.transitionTo(SubscriptionStatus.CANCELED);

        Long companyId = TenantContext.getRequiredCompanyId();
        if (subscription.getAsaasId() != null) {
            asaasGateway.cancelSubscription(companyId, subscription.getAsaasId());
        }

        subscription = subscriptionRepository.save(subscription);

        outboxPublisher.publish("SubscriptionCanceledEvent", "Subscription",
                subscription.getId().toString(), subscriptionMapper.toResponse(subscription));

        log.info("Subscription canceled: id={}, asaasId={}", id, subscription.getAsaasId());
        return subscriptionMapper.toResponse(subscription);
    }

    @Transactional
    @Auditable(action = "SUBSCRIPTION_PAUSE", entity = "Subscription")
    public SubscriptionResponse pause(Long id, boolean confirmCouponRemoval) {
        Subscription subscription = getSubscriptionOrThrow(id);

        if (subscription.getCouponId() != null && !confirmCouponRemoval) {
            throw new BusinessException(
                    "A assinatura possui o cupom '" + subscription.getCouponCode() + "' aplicado. " +
                    "Ao pausar, o cupom sera removido permanentemente. " +
                    "Envie confirmCouponRemoval=true para confirmar.");
        }

        if (subscription.getCouponId() != null) {
            couponService.removeCouponFromSubscription(subscription, "PAUSED");
        }

        subscription.transitionTo(SubscriptionStatus.PAUSED);

        subscription = subscriptionRepository.save(subscription);

        outboxPublisher.publish("SubscriptionPausedEvent", "Subscription",
                subscription.getId().toString(), subscriptionMapper.toResponse(subscription));

        log.info("Subscription paused: id={}", id);
        return subscriptionMapper.toResponse(subscription);
    }

    @Transactional
    @Auditable(action = "SUBSCRIPTION_RESUME", entity = "Subscription")
    public SubscriptionResponse resume(Long id) {
        Subscription subscription = getSubscriptionOrThrow(id);
        subscription.transitionTo(SubscriptionStatus.ACTIVE);

        subscription = subscriptionRepository.save(subscription);

        outboxPublisher.publish("SubscriptionResumedEvent", "Subscription",
                subscription.getId().toString(), subscriptionMapper.toResponse(subscription));

        log.info("Subscription resumed: id={}", id);
        return subscriptionMapper.toResponse(subscription);
    }

    @Transactional
    @Auditable(action = "SUBSCRIPTION_UPDATE_PAYMENT_METHOD", entity = "Subscription")
    public SubscriptionResponse updatePaymentMethod(Long id, UpdatePaymentMethodRequest request) {
        Subscription subscription = getSubscriptionOrThrow(id);

        if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
            throw new BusinessException("Nao e possivel alterar metodo de pagamento de assinatura cancelada.");
        }

        subscription.setBillingType(request.billingType());
        subscription = subscriptionRepository.save(subscription);

        log.info("Subscription payment method updated: id={}, newBillingType={}", id, request.billingType());
        return subscriptionMapper.toResponse(subscription);
    }

    @Transactional
    public void suspend(Long id, String reason) {
        Subscription subscription = getSubscriptionOrThrow(id);
        subscription.transitionTo(SubscriptionStatus.SUSPENDED);

        subscription = subscriptionRepository.save(subscription);

        outboxPublisher.publish("SubscriptionSuspendedEvent", "Subscription",
                subscription.getId().toString(), subscriptionMapper.toResponse(subscription));

        log.info("Subscription suspended: id={}, reason={}", id, reason);
    }

    // ==================== PACKAGE-PRIVATE (used by webhook handler) ====================

    public Subscription findByAsaasId(String asaasId) {
        return subscriptionRepository.findByAsaasId(asaasId).orElse(null);
    }

    // ==================== PRIVATE ====================

    private void syncSubscriptionCharges(Long companyId, Subscription subscription, Company company, Customer customer) {
        try {
            AsaasPageResponse<AsaasPaymentResponse> paymentsPage =
                    asaasGateway.listSubscriptionPayments(companyId, subscription.getAsaasId());

            if (paymentsPage.data() == null || paymentsPage.data().isEmpty()) {
                log.warn("No charges returned from Asaas for subscription asaasId={}", subscription.getAsaasId());
                return;
            }

            for (AsaasPaymentResponse payment : paymentsPage.data()) {
                if (chargeRepository.findByAsaasId(payment.id()).isPresent()) {
                    continue;
                }

                Charge charge = Charge.builder()
                        .company(company)
                        .customer(customer)
                        .subscriptionId(subscription.getId())
                        .asaasId(payment.id())
                        .billingType(BillingType.valueOf(payment.billingType()))
                        .value(payment.value())
                        .dueDate(LocalDate.parse(payment.dueDate()))
                        .status(mapAsaasStatus(payment.status()))
                        .origin(ChargeOrigin.RECURRING)
                        .externalReference(payment.externalReference())
                        .invoiceUrl(payment.invoiceUrl())
                        .boletoUrl(payment.bankSlipUrl())
                        .build();

                chargeRepository.save(charge);
                log.info("Charge synced from Asaas: asaasId={}, subscriptionId={}", payment.id(), subscription.getId());
            }
        } catch (Exception e) {
            log.error("Failed to sync charges for subscription asaasId={}: {}", subscription.getAsaasId(), e.getMessage());
        }
    }

    private ChargeStatus mapAsaasStatus(String asaasStatus) {
        return switch (asaasStatus) {
            case "CONFIRMED" -> ChargeStatus.CONFIRMED;
            case "RECEIVED", "RECEIVED_IN_CASH" -> ChargeStatus.RECEIVED;
            case "OVERDUE" -> ChargeStatus.OVERDUE;
            case "REFUNDED", "REFUND_REQUESTED", "REFUND_IN_PROGRESS" -> ChargeStatus.REFUNDED;
            case "CHARGEBACK_REQUESTED", "CHARGEBACK_DISPUTE", "AWAITING_CHARGEBACK_REVERSAL" -> ChargeStatus.CHARGEBACK;
            case "DUNNING_REQUESTED", "DUNNING_RECEIVED" -> ChargeStatus.OVERDUE;
            default -> ChargeStatus.PENDING;
        };
    }

    private Subscription getSubscriptionOrThrow(Long id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", id));
    }
}
