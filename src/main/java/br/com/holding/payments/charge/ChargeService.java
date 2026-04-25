package br.com.holding.payments.charge;

import br.com.holding.payments.audit.Auditable;
import br.com.holding.payments.charge.dto.*;
import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.common.errors.ResourceNotFoundException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.coupon.*;
import br.com.holding.payments.customer.Customer;
import br.com.holding.payments.customer.CustomerRepository;
import br.com.holding.payments.installment.Installment;
import br.com.holding.payments.installment.InstallmentRepository;
import br.com.holding.payments.integration.asaas.dto.AsaasPixQrCodeResponse;
import br.com.holding.payments.integration.asaas.dto.AsaasBoletoResponse;
import br.com.holding.payments.integration.asaas.gateway.*;
import br.com.holding.payments.outbox.OutboxPublisher;
import br.com.holding.payments.tenant.TenantContext;
import br.com.holding.payments.webhook.WebhookService;
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
public class ChargeService {

    private final ChargeRepository chargeRepository;
    private final CustomerRepository customerRepository;
    private final CompanyRepository companyRepository;
    private final InstallmentRepository installmentRepository;
    private final ChargeMapper chargeMapper;
    private final AsaasGatewayService asaasGateway;
    private final OutboxPublisher outboxPublisher;
    private final WebhookService webhookService;
    private final CouponService couponService;

    @Transactional
    @Auditable(action = "CHARGE_CREATE_PIX", entity = "Charge")
    public ChargeResponse createPixCharge(CreateChargeRequest request) {
        return createCharge(request, BillingType.PIX);
    }

    @Transactional
    @Auditable(action = "CHARGE_CREATE_BOLETO", entity = "Charge")
    public ChargeResponse createBoletoCharge(CreateChargeRequest request) {
        return createCharge(request, BillingType.BOLETO);
    }

    @Transactional
    @Auditable(action = "CHARGE_CREATE_CREDIT_CARD", entity = "Charge")
    public ChargeResponse createCreditCardCharge(CreateChargeRequest request) {
        return createCharge(request, BillingType.CREDIT_CARD);
    }

    @Transactional
    @Auditable(action = "CHARGE_CREATE_UNDEFINED", entity = "Charge")
    public ChargeResponse createUndefinedCharge(CreateChargeRequest request) {
        return createCharge(request, BillingType.UNDEFINED);
    }

    @Transactional
    @Auditable(action = "CHARGE_CREATE_CC_INSTALLMENTS", entity = "Charge")
    public ChargeResponse createCreditCardInstallments(CreateChargeRequest request) {
        validateInstallmentRequest(request);
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            throw new BusinessException("Cupom de desconto nao pode ser aplicado a cobrancas parceladas.");
        }
        return createCharge(request, BillingType.CREDIT_CARD);
    }

    @Transactional
    @Auditable(action = "CHARGE_CREATE_BOLETO_INSTALLMENTS", entity = "Charge")
    public ChargeResponse createBoletoInstallments(CreateChargeRequest request) {
        validateInstallmentRequest(request);
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            throw new BusinessException("Cupom de desconto nao pode ser aplicado a cobrancas parceladas.");
        }
        return createCharge(request, BillingType.BOLETO);
    }

    @Transactional(readOnly = true)
    public Page<ChargeResponse> findAll(ChargeStatus status, ChargeOrigin origin,
                                         LocalDate dueDateFrom, LocalDate dueDateTo,
                                         Long customerId, Pageable pageable) {
        return chargeRepository.findWithFilters(status, origin, dueDateFrom, dueDateTo, customerId, pageable)
                .map(chargeMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ChargeResponse findById(Long id) {
        return chargeMapper.toResponse(getChargeOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PixQrCodeResponse getPixQrCode(Long id) {
        Charge charge = getChargeOrThrow(id);
        if (charge.getBillingType() != BillingType.PIX) {
            throw new BusinessException("QR Code PIX disponivel apenas para cobrancas do tipo PIX");
        }

        Long companyId = TenantContext.getRequiredCompanyId();
        AsaasPixQrCodeResponse qr = asaasGateway.getPixQrCode(companyId, charge.getAsaasId());

        // Cache QR code locally
        if (qr.payload() != null) {
            charge.setPixCopyPaste(qr.payload());
            charge.setPixQrcode(qr.encodedImage());
            chargeRepository.save(charge);
        }

        return new PixQrCodeResponse(qr.encodedImage(), qr.payload(), qr.expirationDate());
    }

    @Transactional(readOnly = true)
    public BoletoLineResponse getBoletoLine(Long id) {
        Charge charge = getChargeOrThrow(id);
        if (charge.getBillingType() != BillingType.BOLETO) {
            throw new BusinessException("Linha digitavel disponivel apenas para cobrancas do tipo BOLETO");
        }

        Long companyId = TenantContext.getRequiredCompanyId();
        AsaasBoletoResponse boleto = asaasGateway.getBoletoIdentificationField(companyId, charge.getAsaasId());
        return new BoletoLineResponse(boleto.identificationField(), boleto.nossoNumero(), boleto.barCode());
    }

    @Transactional
    @Auditable(action = "CHARGE_CANCEL", entity = "Charge")
    public ChargeResponse cancel(Long id) {
        Charge charge = getChargeOrThrow(id);
        charge.transitionTo(ChargeStatus.CANCELED);

        Long companyId = TenantContext.getRequiredCompanyId();
        asaasGateway.cancelPayment(companyId, charge.getAsaasId());

        charge = chargeRepository.save(charge);

        outboxPublisher.publish("ChargeCanceledEvent", "Charge",
                charge.getId().toString(), chargeMapper.toResponse(charge));

        log.info("Charge canceled: id={}, asaasId={}", id, charge.getAsaasId());
        return chargeMapper.toResponse(charge);
    }

    @Transactional
    @Auditable(action = "CHARGE_REFUND", entity = "Charge")
    public ChargeResponse refund(Long id, RefundRequest request) {
        Charge charge = getChargeOrThrow(id);
        charge.transitionTo(ChargeStatus.REFUNDED);

        Long companyId = TenantContext.getRequiredCompanyId();
        BigDecimal refundValue = request.value() != null ? request.value() : charge.getValue();
        asaasGateway.refundPayment(companyId, charge.getAsaasId(), refundValue, request.description());

        charge = chargeRepository.save(charge);

        outboxPublisher.publish("ChargeRefundedEvent", "Charge",
                charge.getId().toString(), chargeMapper.toResponse(charge));

        log.info("Charge refunded: id={}, value={}", id, refundValue);
        return chargeMapper.toResponse(charge);
    }

    @Transactional
    @Auditable(action = "CHARGE_REGENERATE_BOLETO", entity = "Charge")
    public ChargeResponse regenerateBoleto(Long id) {
        Charge charge = getChargeOrThrow(id);
        if (charge.getBillingType() != BillingType.BOLETO) {
            throw new BusinessException("Regeneracao disponivel apenas para cobrancas do tipo BOLETO");
        }
        if (charge.getStatus() != ChargeStatus.PENDING && charge.getStatus() != ChargeStatus.OVERDUE) {
            throw new BusinessException("Apenas cobrancas PENDING ou OVERDUE podem ter boleto regenerado. Status atual: " + charge.getStatus());
        }

        Long companyId = TenantContext.getRequiredCompanyId();
        AsaasBoletoResponse boleto = asaasGateway.getBoletoIdentificationField(companyId, charge.getAsaasId());
        charge.setBoletoUrl(boleto.identificationField());
        charge = chargeRepository.save(charge);

        log.info("Boleto regenerated: chargeId={}, asaasId={}", id, charge.getAsaasId());
        return chargeMapper.toResponse(charge);
    }

    @Transactional
    @Auditable(action = "CHARGE_RECEIVED_IN_CASH", entity = "Charge")
    public ChargeResponse markAsReceivedInCash(Long id) {
        Charge charge = getChargeOrThrow(id);
        charge.transitionTo(ChargeStatus.RECEIVED);
        charge = chargeRepository.save(charge);

        outboxPublisher.publish("ChargePaidEvent", "Charge",
                charge.getId().toString(), chargeMapper.toResponse(charge));

        log.info("Charge marked as received in cash: id={}", id);
        return chargeMapper.toResponse(charge);
    }

    @Transactional
    @Auditable(action = "CHARGE_RESEND_NOTIFICATION", entity = "Charge")
    public ChargeResponse resendNotification(Long id) {
        Charge charge = getChargeOrThrow(id);
        if (charge.getStatus() == ChargeStatus.CANCELED || charge.getStatus() == ChargeStatus.REFUNDED) {
            throw new BusinessException("Nao e possivel reenviar notificacao para cobranca " + charge.getStatus());
        }

        outboxPublisher.publish("ChargeNotificationResendEvent", "Charge",
                charge.getId().toString(), chargeMapper.toResponse(charge));

        log.info("Charge notification resend requested: id={}, asaasId={}", id, charge.getAsaasId());
        return chargeMapper.toResponse(charge);
    }

    // ==================== PRIVATE ====================

    private ChargeResponse createCharge(CreateChargeRequest request, BillingType billingType) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.getReferenceById(companyId);
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

        if (customer.getAsaasId() == null) {
            throw new BusinessException("Cliente nao possui asaas_id. Sincronize com o Asaas primeiro.");
        }

        // If coupon code provided, validate and calculate discount (usage recorded after charge is saved)
        BigDecimal finalValue = request.value();
        String appliedCouponCode = null;
        Long appliedCouponId = null;
        BigDecimal appliedDiscountAmount = null;
        BigDecimal appliedOriginalValue = null;
        CouponCalculator.CouponDiscountResult couponResult = null;

        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            String upperCode = request.couponCode().toUpperCase();
            Coupon coupon = couponService.findActiveEntityByCode(upperCode);
            couponResult = couponService.validateAndCalculateForCharge(upperCode, customer.getId(), request.value());
            finalValue = couponResult.finalValue();
            appliedDiscountAmount = couponResult.discountAmount();
            appliedOriginalValue = request.value();
            appliedCouponCode = upperCode;
            appliedCouponId = coupon.getId();
        }

        // Create payment on Asaas (use finalValue after coupon discount)
        AsaasPaymentData paymentData = buildPaymentData(request, customer, billingType, finalValue);
        AsaasPaymentResult asaasResult = asaasGateway.createPayment(companyId, paymentData);

        // Handle installment if applicable
        Installment installment = null;
        if (request.installmentCount() != null && request.installmentCount() > 1 && asaasResult.installmentId() != null) {
            installment = installmentRepository.findByAsaasId(asaasResult.installmentId())
                    .orElseGet(() -> installmentRepository.save(Installment.builder()
                            .company(company)
                            .customer(customer)
                            .asaasId(asaasResult.installmentId())
                            .totalValue(request.value())
                            .installmentCount(request.installmentCount())
                            .billingType(billingType)
                            .build()));
        }

        Charge charge = Charge.builder()
                .company(company)
                .customer(customer)
                .asaasId(asaasResult.asaasId())
                .billingType(billingType)
                .value(asaasResult.value())
                .dueDate(request.dueDate())
                .origin(request.origin() != null ? request.origin() : ChargeOrigin.API)
                .externalReference(request.externalReference())
                .invoiceUrl(asaasResult.invoiceUrl())
                .boletoUrl(asaasResult.bankSlipUrl())
                .installment(installment)
                .installmentNumber(asaasResult.installmentNumber())
                .couponId(appliedCouponId)
                .couponCode(appliedCouponCode)
                .discountAmount(appliedDiscountAmount)
                .originalValue(appliedOriginalValue)
                .build();

        charge = chargeRepository.save(charge);

        // Record coupon usage now that charge ID is available
        if (appliedCouponCode != null && couponResult != null) {
            couponService.recordChargeUsage(
                    appliedCouponCode, customer.getId(), charge.getId(),
                    appliedOriginalValue, couponResult);
        }

        if (billingType == BillingType.PIX && charge.getAsaasId() != null) {
            try {
                AsaasPixQrCodeResponse qr = asaasGateway.getPixQrCode(companyId, charge.getAsaasId());
                if (qr.payload() != null) {
                    charge.setPixCopyPaste(qr.payload());
                    charge.setPixQrcode(qr.encodedImage());
                    charge = chargeRepository.save(charge);
                }
            } catch (Exception e) {
                log.warn("Falha ao obter QR Code PIX no Asaas apos criar cobranca id={}, asaasId={}: {}",
                        charge.getId(), charge.getAsaasId(), e.getMessage());
            }
        }

        outboxPublisher.publish("ChargeCreatedEvent", "Charge",
                charge.getId().toString(), chargeMapper.toResponse(charge));

        // Cross-reconciliation: accelerate deferred webhook events for this asaas_id
        if (charge.getAsaasId() != null) {
            webhookService.accelerateDeferredForAsaasId(charge.getAsaasId());
        }

        log.info("Charge created: id={}, asaasId={}, type={}, value={}",
                charge.getId(), charge.getAsaasId(), billingType, charge.getValue());

        return chargeMapper.toResponse(charge);
    }

    private AsaasPaymentData buildPaymentData(CreateChargeRequest request, Customer customer, BillingType billingType, BigDecimal finalValue) {
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

        return new AsaasPaymentData(
                customer.getAsaasId(),
                billingType.name(),
                finalValue,
                request.dueDate().toString(),
                request.description(),
                request.externalReference(),
                request.installmentCount(),
                request.installmentValue(),
                creditCard,
                holderInfo,
                request.creditCardToken(),
                request.remoteIp()
        );
    }

    private void validateInstallmentRequest(CreateChargeRequest request) {
        if (request.installmentCount() == null || request.installmentCount() < 2) {
            throw new BusinessException("Numero de parcelas deve ser pelo menos 2");
        }
    }

    private Charge getChargeOrThrow(Long id) {
        return chargeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Charge", id));
    }
}
