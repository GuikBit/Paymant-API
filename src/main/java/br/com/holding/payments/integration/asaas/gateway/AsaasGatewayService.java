package br.com.holding.payments.integration.asaas.gateway;

import br.com.holding.payments.integration.asaas.client.*;
import br.com.holding.payments.integration.asaas.dto.*;
import br.com.holding.payments.integration.asaas.mapper.AsaasMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Fachada de dominio para operacoes no Asaas.
 * Isola DTOs do Asaas do resto da aplicacao, expondo apenas objetos de dominio.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsaasGatewayService {

    private final AsaasCustomerClient customerClient;
    private final AsaasPaymentClient paymentClient;
    private final AsaasSubscriptionClient subscriptionClient;
    private final AsaasInstallmentClient installmentClient;
    private final AsaasMapper mapper;

    // ==================== CUSTOMERS ====================

    public AsaasCustomerResult createCustomer(Long companyId, AsaasCustomerData data) {
        AsaasCustomerRequest request = mapper.toAsaasCustomerRequest(data);
        AsaasCustomerResponse response = customerClient.create(companyId, request);
        log.info("Customer created in Asaas: asaasId={}, company={}", response.id(), companyId);
        return mapper.toCustomerResult(response);
    }

    public AsaasCustomerResult getCustomer(Long companyId, String asaasId) {
        AsaasCustomerResponse response = customerClient.getById(companyId, asaasId);
        return mapper.toCustomerResult(response);
    }

    public AsaasCustomerResult updateCustomer(Long companyId, String asaasId, AsaasCustomerData data) {
        AsaasCustomerRequest request = mapper.toAsaasCustomerRequest(data);
        AsaasCustomerResponse response = customerClient.update(companyId, asaasId, request);
        log.info("Customer updated in Asaas: asaasId={}, company={}", asaasId, companyId);
        return mapper.toCustomerResult(response);
    }

    // ==================== PAYMENTS ====================

    public AsaasPaymentResult createPayment(Long companyId, AsaasPaymentData data) {
        AsaasPaymentRequest request = mapper.toAsaasPaymentRequest(data);
        AsaasPaymentResponse response = paymentClient.create(companyId, request);
        log.info("Payment created in Asaas: asaasId={}, company={}, value={}",
                response.id(), companyId, response.value());
        return mapper.toPaymentResult(response);
    }

    public AsaasPaymentResult getPayment(Long companyId, String asaasId) {
        AsaasPaymentResponse response = paymentClient.getById(companyId, asaasId);
        return mapper.toPaymentResult(response);
    }

    public AsaasPaymentResult cancelPayment(Long companyId, String asaasId) {
        AsaasPaymentResponse response = paymentClient.cancel(companyId, asaasId);
        log.info("Payment canceled in Asaas: asaasId={}, company={}", asaasId, companyId);
        return mapper.toPaymentResult(response);
    }

    public AsaasPaymentResult refundPayment(Long companyId, String asaasId, BigDecimal value, String description) {
        AsaasRefundRequest request = new AsaasRefundRequest(value, description);
        AsaasPaymentResponse response = paymentClient.refund(companyId, asaasId, request);
        log.info("Payment refunded in Asaas: asaasId={}, company={}, value={}", asaasId, companyId, value);
        return mapper.toPaymentResult(response);
    }

    public AsaasPixQrCodeResponse getPixQrCode(Long companyId, String asaasId) {
        return paymentClient.getPixQrCode(companyId, asaasId);
    }

    public AsaasBoletoResponse getBoletoIdentificationField(Long companyId, String asaasId) {
        return paymentClient.getBoletoIdentificationField(companyId, asaasId);
    }

    // ==================== SUBSCRIPTIONS ====================

    public AsaasPageResponse<AsaasPaymentResponse> listSubscriptionPayments(Long companyId, String subscriptionAsaasId) {
        return paymentClient.listBySubscription(companyId, subscriptionAsaasId);
    }

    public AsaasSubscriptionResult createSubscription(Long companyId, AsaasSubscriptionData data) {
        AsaasSubscriptionRequest request = mapper.toAsaasSubscriptionRequest(data);
        AsaasSubscriptionResponse response = subscriptionClient.create(companyId, request);
        log.info("Subscription created in Asaas: asaasId={}, company={}", response.id(), companyId);
        return mapper.toSubscriptionResult(response);
    }

    public AsaasSubscriptionResult getSubscription(Long companyId, String asaasId) {
        AsaasSubscriptionResponse response = subscriptionClient.getById(companyId, asaasId);
        return mapper.toSubscriptionResult(response);
    }

    public AsaasSubscriptionResult cancelSubscription(Long companyId, String asaasId) {
        AsaasSubscriptionResponse response = subscriptionClient.cancel(companyId, asaasId);
        log.info("Subscription canceled in Asaas: asaasId={}, company={}", asaasId, companyId);
        return mapper.toSubscriptionResult(response);
    }

    public void updateSubscriptionValue(Long companyId, String asaasSubscriptionId, BigDecimal newValue) {
        log.info("Updating Asaas subscription value: asaasId={}, companyId={}, newValue={}",
                asaasSubscriptionId, companyId, newValue);
        subscriptionClient.update(companyId, asaasSubscriptionId, Map.of("value", newValue));
    }

    // ==================== INSTALLMENTS ====================

    public AsaasInstallmentResponse getInstallment(Long companyId, String asaasId) {
        return installmentClient.getById(companyId, asaasId);
    }

    public void cancelInstallment(Long companyId, String asaasId) {
        installmentClient.cancel(companyId, asaasId);
        log.info("Installment canceled in Asaas: asaasId={}, company={}", asaasId, companyId);
    }
}
