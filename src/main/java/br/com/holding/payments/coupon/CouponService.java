package br.com.holding.payments.coupon;

import br.com.holding.payments.audit.Auditable;
import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.common.errors.ResourceNotFoundException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.coupon.dto.*;
import br.com.holding.payments.customer.Customer;
import br.com.holding.payments.customer.CustomerRepository;
import br.com.holding.payments.integration.asaas.gateway.AsaasGatewayService;
import br.com.holding.payments.plan.Plan;
import br.com.holding.payments.plan.PlanCycle;
import br.com.holding.payments.plan.PlanRepository;
import br.com.holding.payments.plan.PlanService;
import br.com.holding.payments.subscription.Subscription;
import br.com.holding.payments.subscription.SubscriptionRepository;
import br.com.holding.payments.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final CompanyRepository companyRepository;
    private final CustomerRepository customerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final PlanService planService;
    private final AsaasGatewayService asaasGateway;
    private final CouponMapper couponMapper;

    private static final BigDecimal MAX_PERCENTAGE_DISCOUNT = new BigDecimal("90");

    // ==================== CRUD ====================

    @Transactional
    @Auditable(action = "COUPON_CREATE", entity = "Coupon")
    public CouponResponse create(CreateCouponRequest request) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.getReferenceById(companyId);

        String code = request.code().toUpperCase();

        if (couponRepository.existsByCodeAndCompanyId(code, companyId)) {
            throw new BusinessException("Ja existe um cupom com o codigo '" + code + "' para esta empresa.");
        }

        if (request.discountType() == DiscountType.PERCENTAGE
                && request.discountValue().compareTo(MAX_PERCENTAGE_DISCOUNT) > 0) {
            throw new BusinessException("Desconto percentual nao pode ser maior que 90%.");
        }

        if (request.validFrom() != null && request.validUntil() != null
                && !request.validUntil().isAfter(request.validFrom())) {
            throw new BusinessException("Data de validade final deve ser posterior a data de inicio.");
        }

        CouponApplicationType applicationType = request.applicationType() != null
                ? request.applicationType()
                : CouponApplicationType.FIRST_CHARGE;

        // If scope is CHARGE, force applicationType to FIRST_CHARGE
        if (request.scope() == CouponScope.CHARGE) {
            applicationType = CouponApplicationType.FIRST_CHARGE;
        }

        Integer recurrenceMonths = request.recurrenceMonths();
        // recurrenceMonths is only relevant for RECURRING + SUBSCRIPTION (null = permanent)

        Coupon coupon = Coupon.builder()
                .company(company)
                .code(code)
                .description(request.description())
                .discountType(request.discountType())
                .discountValue(request.discountValue())
                .scope(request.scope())
                .applicationType(applicationType)
                .recurrenceMonths(recurrenceMonths)
                .validFrom(request.validFrom())
                .validUntil(request.validUntil())
                .maxUses(request.maxUses())
                .maxUsesPerCustomer(request.maxUsesPerCustomer())
                .usageCount(0)
                .active(true)
                .allowedPlans(request.allowedPlans())
                .allowedCustomers(request.allowedCustomers())
                .allowedCycle(blankToNull(request.allowedCycle()))
                .build();

        coupon = couponRepository.save(coupon);
        log.info("Coupon created: id={}, code={}, discountType={}, discountValue={}",
                coupon.getId(), coupon.getCode(), coupon.getDiscountType(), coupon.getDiscountValue());
        return couponMapper.toResponse(coupon);
    }

    @Transactional(readOnly = true)
    public CouponResponse findById(Long id) {
        Coupon coupon = getCouponOrThrow(id);
        return couponMapper.toResponse(coupon);
    }

    @Transactional(readOnly = true)
    public CouponResponse findByCode(String code) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Coupon coupon = couponRepository.findByCodeAndCompanyId(code.toUpperCase(), companyId)
                .orElseThrow(() -> new BusinessException("Cupom com codigo '" + code + "' nao encontrado."));
        return couponMapper.toResponse(coupon);
    }

    @Transactional(readOnly = true)
    public Page<CouponResponse> findAll(Pageable pageable) {
        return couponRepository.findAll(pageable).map(couponMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CouponResponse> findActive(Pageable pageable) {
        return couponRepository.findAllActive(pageable).map(couponMapper::toResponse);
    }

    @Transactional
    @Auditable(action = "COUPON_UPDATE", entity = "Coupon")
    public CouponResponse update(Long id, UpdateCouponRequest request) {
        Coupon coupon = getCouponOrThrow(id);

        // code and scope are NOT updatable
        if (request.description() != null) coupon.setDescription(request.description());
        if (request.discountType() != null) coupon.setDiscountType(request.discountType());
        if (request.discountValue() != null) coupon.setDiscountValue(request.discountValue());
        if (request.applicationType() != null) coupon.setApplicationType(request.applicationType());
        if (request.recurrenceMonths() != null) coupon.setRecurrenceMonths(request.recurrenceMonths());
        if (request.validFrom() != null) coupon.setValidFrom(request.validFrom());
        if (request.validUntil() != null) coupon.setValidUntil(request.validUntil());
        if (request.maxUses() != null) coupon.setMaxUses(request.maxUses());
        if (request.maxUsesPerCustomer() != null) coupon.setMaxUsesPerCustomer(request.maxUsesPerCustomer());
        coupon.setAllowedPlans(blankToNull(request.allowedPlans()));
        coupon.setAllowedCustomers(blankToNull(request.allowedCustomers()));
        coupon.setAllowedCycle(blankToNull(request.allowedCycle()));

        // Re-validate discount rules after applying changes
        if (coupon.getDiscountType() == DiscountType.PERCENTAGE
                && coupon.getDiscountValue().compareTo(MAX_PERCENTAGE_DISCOUNT) > 0) {
            throw new BusinessException("Desconto percentual nao pode ser maior que 90%.");
        }

        if (coupon.getValidFrom() != null && coupon.getValidUntil() != null
                && !coupon.getValidUntil().isAfter(coupon.getValidFrom())) {
            throw new BusinessException("Data de validade final deve ser posterior a data de inicio.");
        }

        coupon = couponRepository.save(coupon);
        log.info("Coupon updated: id={}, code={}", coupon.getId(), coupon.getCode());
        return couponMapper.toResponse(coupon);
    }

    @Transactional
    @Auditable(action = "COUPON_SOFT_DELETE", entity = "Coupon")
    public void softDelete(Long id) {
        Coupon coupon = getCouponOrThrow(id);
        coupon.softDelete();
        couponRepository.save(coupon);
        log.info("Coupon soft-deleted: id={}, code={}", id, coupon.getCode());
    }

    @Transactional
    @Auditable(action = "COUPON_ACTIVATE", entity = "Coupon")
    public CouponResponse activate(Long id) {
        Coupon coupon = getCouponOrThrow(id);
        coupon.setActive(true);
        coupon = couponRepository.save(coupon);
        return couponMapper.toResponse(coupon);
    }

    @Transactional(readOnly = true)
    public Page<CouponUsageResponse> getUsages(Long id, Pageable pageable) {
        getCouponOrThrow(id); // ensure coupon exists
        return couponUsageRepository.findByCouponId(id, pageable).map(couponMapper::toUsageResponse);
    }

    // ==================== VALIDATION ====================

    @Transactional(readOnly = true)
    public CouponValidationResponse validatePublic(ValidateCouponRequest request) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Coupon coupon = couponRepository.findByCodeAndCompanyId(request.couponCode().toUpperCase(), companyId)
                .orElse(null);

        // Check 1: Exists, active, not deleted
        if (coupon == null || !Boolean.TRUE.equals(coupon.getActive()) || coupon.isDeleted()) {
            return CouponValidationResponse.invalid("Cupom nao encontrado ou inativo.");
        }

        // Check 2: Within period
        if (!coupon.isWithinPeriod()) {
            return CouponValidationResponse.invalid("Cupom esta fora do periodo de validade.");
        }

        // Check 3: Global limit
        if (coupon.hasReachedGlobalLimit()) {
            return CouponValidationResponse.invalid("Cupom atingiu o limite maximo de utilizacoes.");
        }

        // Check 5: Scope matches
        if (request.scope() != coupon.getScope()) {
            return CouponValidationResponse.invalid("Cupom nao e valido para este tipo de operacao.");
        }

        // Check 6: Plan allowed
        if (coupon.getScope() == CouponScope.SUBSCRIPTION && coupon.getAllowedPlans() != null
                && request.planCode() != null && !isPlanAllowed(coupon.getAllowedPlans(), request.planCode())) {
            return CouponValidationResponse.invalid("Cupom nao e valido para o plano selecionado.");
        }

        // Check 8: Cycle matches
        if (coupon.getScope() == CouponScope.SUBSCRIPTION
                && blankToNull(coupon.getAllowedCycle()) != null
                && request.cycle() != null
                && !coupon.getAllowedCycle().equalsIgnoreCase(request.cycle())) {
            return CouponValidationResponse.invalid("Cupom nao e valido para o ciclo de cobranca selecionado.");
        }

        BigDecimal effectiveValue = resolveValue(request, coupon.getCompany().getId());
        if (effectiveValue != null) {
            return buildSuccessResponse(coupon, effectiveValue);
        }

        return new CouponValidationResponse(
                true,
                "Cupom valido.",
                coupon.getDiscountType(),
                coupon.getApplicationType(),
                coupon.getDiscountType() == DiscountType.PERCENTAGE ? coupon.getDiscountValue() : null,
                null,
                null,
                null
        );
    }

    @Transactional(readOnly = true)
    public CouponValidationResponse validateAuthenticated(ValidateCouponRequest request) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Coupon coupon = couponRepository.findByCodeAndCompanyId(request.couponCode().toUpperCase(), companyId)
                .orElse(null);

        // Check 1: Exists, active, not deleted
        if (coupon == null || !Boolean.TRUE.equals(coupon.getActive()) || coupon.isDeleted()) {
            return CouponValidationResponse.invalid("Cupom nao encontrado ou inativo.");
        }

        // Check 2: Within period
        if (!coupon.isWithinPeriod()) {
            return CouponValidationResponse.invalid("Cupom esta fora do periodo de validade.");
        }

        // Check 3: Global limit
        if (coupon.hasReachedGlobalLimit()) {
            return CouponValidationResponse.invalid("Cupom atingiu o limite maximo de utilizacoes.");
        }

        // Check 4: Per-customer limit
        if (request.customerId() != null && coupon.getMaxUsesPerCustomer() != null) {
            long customerUsages = couponUsageRepository.countByCouponIdAndCustomerId(coupon.getId(), request.customerId());
            if (customerUsages >= coupon.getMaxUsesPerCustomer()) {
                return CouponValidationResponse.invalid("Cupom ja foi utilizado o numero maximo de vezes para este cliente.");
            }
        }

        // Check 5: Scope matches
        if (request.scope() != coupon.getScope()) {
            return CouponValidationResponse.invalid("Cupom nao e valido para este tipo de operacao.");
        }

        // Check 6: Plan allowed
        if (coupon.getScope() == CouponScope.SUBSCRIPTION && coupon.getAllowedPlans() != null
                && request.planCode() != null && !isPlanAllowed(coupon.getAllowedPlans(), request.planCode())) {
            return CouponValidationResponse.invalid("Cupom nao e valido para o plano selecionado.");
        }

        // Check 7: Customer allowed
        if (coupon.getAllowedCustomers() != null && request.customerId() != null
                && !isCustomerAllowed(coupon.getAllowedCustomers(), request.customerId())) {
            return CouponValidationResponse.invalid("Cupom nao e valido para este cliente.");
        }

        // Check 8: Cycle matches
        if (coupon.getScope() == CouponScope.SUBSCRIPTION
                && blankToNull(coupon.getAllowedCycle()) != null
                && request.cycle() != null
                && !coupon.getAllowedCycle().equalsIgnoreCase(request.cycle())) {
            return CouponValidationResponse.invalid("Cupom nao e valido para o ciclo de cobranca selecionado.");
        }

        BigDecimal effectiveValue = resolveValue(request, coupon.getCompany().getId());
        if (effectiveValue != null) {
            return buildSuccessResponse(coupon, effectiveValue);
        }

        return new CouponValidationResponse(
                true,
                "Cupom valido.",
                coupon.getDiscountType(),
                coupon.getApplicationType(),
                coupon.getDiscountType() == DiscountType.PERCENTAGE ? coupon.getDiscountValue() : null,
                null,
                null,
                null
        );
    }

    // ==================== APPLICATION ====================

    /**
     * Validates coupon for subscription and calculates discount WITHOUT recording usage.
     * Call {@link #recordSubscriptionUsage} after the subscription is persisted to link the IDs.
     */
    @Transactional
    public CouponCalculator.CouponDiscountResult validateAndCalculateForSubscription(
            Long couponId, Long customerId, String planCode, String cycle, BigDecimal effectivePrice) {

        Coupon coupon = getCouponOrThrow(couponId);

        // Full validation (all 8 checks)
        ValidateCouponRequest validateRequest = new ValidateCouponRequest(
                coupon.getCode(), CouponScope.SUBSCRIPTION, planCode, cycle, effectivePrice, customerId);
        CouponValidationResponse validation = validateAuthenticated(validateRequest);
        if (!validation.valid()) {
            throw new BusinessException(validation.message());
        }

        // Increment usage count atomically (reserve the slot)
        int updated = couponRepository.incrementUsageCount(coupon.getId());
        if (updated == 0) {
            throw new BusinessException("Cupom atingiu o limite maximo de utilizacoes.");
        }

        return CouponCalculator.calculate(coupon.getDiscountType(), coupon.getDiscountValue(), effectivePrice);
    }

    /**
     * Records the coupon usage AFTER the subscription has been persisted.
     */
    @Transactional
    public void recordSubscriptionUsage(Long couponId, Long customerId, Long subscriptionId,
                                         String planCode, String cycle,
                                         BigDecimal originalValue, CouponCalculator.CouponDiscountResult result) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Coupon coupon = getCouponOrThrow(couponId);
        Customer customer = customerRepository.getReferenceById(customerId);
        Company company = companyRepository.getReferenceById(companyId);

        CouponUsage usage = CouponUsage.builder()
                .company(company)
                .coupon(coupon)
                .couponCode(coupon.getCode())
                .customer(customer)
                .subscriptionId(subscriptionId)
                .originalValue(originalValue)
                .discountAmount(result.discountAmount())
                .finalValue(result.finalValue())
                .planCode(planCode)
                .cycle(cycle)
                .build();
        couponUsageRepository.save(usage);

        log.info("Coupon {} usage recorded for subscription {}, customer {}, discount={}",
                coupon.getCode(), subscriptionId, customerId, result.discountAmount());
    }

    /**
     * Validates coupon for charge and calculates discount WITHOUT recording usage.
     * Call {@link #recordChargeUsage} after the charge is persisted to link the IDs.
     */
    @Transactional
    public CouponCalculator.CouponDiscountResult validateAndCalculateForCharge(
            String couponCode, Long customerId, BigDecimal chargeValue) {

        Long companyId = TenantContext.getRequiredCompanyId();
        Coupon coupon = couponRepository.findByCodeAndCompanyId(couponCode.toUpperCase(), companyId)
                .orElseThrow(() -> new BusinessException("Cupom com codigo '" + couponCode + "' nao encontrado."));

        // Validate (checks 1-5, 7 - no plan or cycle checks for charges)
        ValidateCouponRequest validateRequest = new ValidateCouponRequest(
                coupon.getCode(), CouponScope.CHARGE, null, null, chargeValue, customerId);
        CouponValidationResponse validation = validateAuthenticated(validateRequest);
        if (!validation.valid()) {
            throw new BusinessException(validation.message());
        }

        // Check customer hasn't used this coupon for a charge before
        long chargeUsages = couponUsageRepository.countChargeUsagesByCustomer(coupon.getId(), customerId);
        if (chargeUsages > 0) {
            throw new BusinessException("Cliente ja utilizou este cupom em uma cobranca.");
        }

        // Increment usage count atomically (reserve the slot)
        int updated = couponRepository.incrementUsageCount(coupon.getId());
        if (updated == 0) {
            throw new BusinessException("Cupom atingiu o limite maximo de utilizacoes.");
        }

        return CouponCalculator.calculate(coupon.getDiscountType(), coupon.getDiscountValue(), chargeValue);
    }

    /**
     * Records the coupon usage AFTER the charge has been persisted.
     */
    @Transactional
    public void recordChargeUsage(String couponCode, Long customerId, Long chargeId,
                                   BigDecimal originalValue, CouponCalculator.CouponDiscountResult result) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Coupon coupon = couponRepository.findByCodeAndCompanyId(couponCode.toUpperCase(), companyId)
                .orElseThrow(() -> new BusinessException("Cupom com codigo '" + couponCode + "' nao encontrado."));
        Customer customer = customerRepository.getReferenceById(customerId);
        Company company = companyRepository.getReferenceById(companyId);

        CouponUsage usage = CouponUsage.builder()
                .company(company)
                .coupon(coupon)
                .couponCode(coupon.getCode())
                .customer(customer)
                .chargeId(chargeId)
                .originalValue(originalValue)
                .discountAmount(result.discountAmount())
                .finalValue(result.finalValue())
                .build();
        couponUsageRepository.save(usage);

        log.info("Coupon {} usage recorded for charge {}, customer {}, discount={}",
                coupon.getCode(), chargeId, customerId, result.discountAmount());
    }

    @Transactional
    public void handlePaymentReceived(Subscription subscription) {
        if (subscription.getCouponUsesRemaining() == null) {
            // Permanent coupon, do nothing
            return;
        }

        if (subscription.getCouponUsesRemaining() > 0) {
            int remaining = subscription.getCouponUsesRemaining() - 1;
            subscription.setCouponUsesRemaining(remaining);
            subscriptionRepository.save(subscription);

            if (remaining == 0) {
                removeCouponFromSubscription(subscription, "COUPON_EXPIRED");
            }

            log.info("Coupon uses remaining decremented for subscription {}: remaining={}",
                    subscription.getId(), remaining);
        }
    }

    @Transactional
    public void removeCouponFromSubscription(Subscription subscription, String reason) {
        // Store coupon info for logging before clearing
        Long couponId = subscription.getCouponId();
        String couponCode = subscription.getCouponCode();

        // Clear coupon fields
        subscription.setCouponId(null);
        subscription.setCouponCode(null);
        subscription.setCouponDiscountAmount(null);
        subscription.setCouponUsesRemaining(null);

        // Update subscription value in Asaas to full price (effectivePrice)
        Long companyId = subscription.getCompany().getId();
        asaasGateway.updateSubscriptionValue(companyId, subscription.getAsaasId(), subscription.getEffectivePrice());

        subscriptionRepository.save(subscription);

        log.info("Coupon {} removed from subscription {}, reason={}",
                couponCode, subscription.getId(), reason);
    }

    // ==================== ENTITY LOOKUP (package-private / used by other services) ====================

    @Transactional(readOnly = true)
    public Coupon findActiveEntityByCode(String code) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Coupon coupon = couponRepository.findByCodeAndCompanyId(code.toUpperCase(), companyId)
                .orElseThrow(() -> new BusinessException("Cupom com codigo '" + code + "' nao encontrado."));
        if (!Boolean.TRUE.equals(coupon.getActive()) || coupon.isDeleted()) {
            throw new BusinessException("Cupom '" + code + "' esta inativo ou foi removido.");
        }
        return coupon;
    }

    // ==================== PRIVATE HELPERS ====================

    private Coupon getCouponOrThrow(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", id));
    }

    private CouponValidationResponse buildSuccessResponse(Coupon coupon, BigDecimal originalValue) {
        CouponCalculator.CouponDiscountResult result = CouponCalculator.calculate(
                coupon.getDiscountType(), coupon.getDiscountValue(), originalValue);

        BigDecimal percentualDiscount = null;
        if (coupon.getDiscountType() == DiscountType.PERCENTAGE) {
            percentualDiscount = coupon.getDiscountValue();
        } else if (originalValue.compareTo(BigDecimal.ZERO) > 0) {
            percentualDiscount = result.discountAmount()
                    .multiply(new BigDecimal("100"))
                    .divide(originalValue, 2, RoundingMode.HALF_UP);
        }

        return new CouponValidationResponse(
                true,
                "Cupom valido.",
                coupon.getDiscountType(),
                coupon.getApplicationType(),
                percentualDiscount,
                result.discountAmount(),
                originalValue,
                result.finalValue()
        );
    }

    private boolean isPlanAllowed(String allowedPlansJson, String planCode) {
        return allowedPlansJson.contains("\"" + planCode + "\"");
    }

    private boolean isCustomerAllowed(String allowedCustomersJson, Long customerId) {
        return allowedCustomersJson.contains("\"" + customerId + "\"")
                || allowedCustomersJson.contains(String.valueOf(customerId));
    }

    private static String blankToNull(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value;
    }

    private BigDecimal resolveValue(ValidateCouponRequest request, Long companyId) {
        if (request.value() != null) {
            return request.value();
        }
        if (request.scope() != CouponScope.SUBSCRIPTION
                || blankToNull(request.planCode()) == null
                || blankToNull(request.cycle()) == null) {
            return null;
        }
        Plan plan = planRepository
                .findByCodigoAndCompanyIdAndActiveTrue(request.planCode(), companyId)
                .orElse(null);
        if (plan == null) {
            return null;
        }
        PlanCycle cycle;
        try {
            cycle = PlanCycle.valueOf(request.cycle().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
        return planService.getEffectivePrice(plan, cycle);
    }
}
