package br.com.holding.payments.integration.asaas.mapper;

import br.com.holding.payments.integration.asaas.dto.*;
import br.com.holding.payments.integration.asaas.gateway.*;
import org.springframework.stereotype.Component;

@Component
public class AsaasMapper {

    // ==================== CUSTOMER ====================

    public AsaasCustomerRequest toAsaasCustomerRequest(AsaasCustomerData data) {
        return new AsaasCustomerRequest(
                data.name(),
                data.cpfCnpj(),
                data.email(),
                data.phone(),
                data.mobilePhone(),
                data.address(),
                data.addressNumber(),
                data.complement(),
                data.province(),
                data.postalCode(),
                data.externalReference(),
                null, null, null, null, null
        );
    }

    public AsaasCustomerResult toCustomerResult(AsaasCustomerResponse response) {
        return new AsaasCustomerResult(
                response.id(),
                response.name(),
                response.cpfCnpj(),
                response.email(),
                response.phone(),
                response.personType()
        );
    }

    // ==================== PAYMENT ====================

    public AsaasPaymentRequest toAsaasPaymentRequest(AsaasPaymentData data) {
        return new AsaasPaymentRequest(
                data.customerAsaasId(),
                data.billingType(),
                data.value(),
                data.dueDate(),
                data.description(),
                data.externalReference(),
                data.installmentCount(),
                data.installmentValue(),
                null, null, null, null,
                mapCreditCard(data.creditCard()),
                mapCreditCardHolderInfo(data.creditCardHolderInfo()),
                data.creditCardToken(),
                data.remoteIp()
        );
    }

    public AsaasPaymentResult toPaymentResult(AsaasPaymentResponse response) {
        return new AsaasPaymentResult(
                response.id(),
                response.customer(),
                response.billingType(),
                response.value(),
                response.netValue(),
                response.status(),
                response.dueDate(),
                response.paymentDate(),
                response.invoiceUrl(),
                response.bankSlipUrl(),
                response.installment(),
                response.installmentNumber()
        );
    }

    // ==================== SUBSCRIPTION ====================

    public AsaasSubscriptionRequest toAsaasSubscriptionRequest(AsaasSubscriptionData data) {
        return new AsaasSubscriptionRequest(
                data.customerAsaasId(),
                data.billingType(),
                data.value(),
                data.nextDueDate(),
                data.cycle(),
                data.description(),
                data.externalReference(),
                null, null, null,
                mapCreditCard(data.creditCard()),
                mapCreditCardHolderInfo(data.creditCardHolderInfo()),
                data.creditCardToken(),
                data.remoteIp()
        );
    }

    public AsaasSubscriptionResult toSubscriptionResult(AsaasSubscriptionResponse response) {
        return new AsaasSubscriptionResult(
                response.id(),
                response.customer(),
                response.billingType(),
                response.value(),
                response.nextDueDate(),
                response.cycle(),
                response.status()
        );
    }

    // ==================== HELPERS ====================

    private AsaasPaymentRequest.AsaasCreditCardRequest mapCreditCard(AsaasPaymentData.CreditCardData data) {
        if (data == null) return null;
        return new AsaasPaymentRequest.AsaasCreditCardRequest(
                data.holderName(), data.number(), data.expiryMonth(), data.expiryYear(), data.ccv()
        );
    }

    private AsaasPaymentRequest.AsaasCreditCardHolderInfoRequest mapCreditCardHolderInfo(AsaasPaymentData.CreditCardHolderData data) {
        if (data == null) return null;
        return new AsaasPaymentRequest.AsaasCreditCardHolderInfoRequest(
                data.name(), data.email(), data.cpfCnpj(), data.postalCode(),
                data.addressNumber(), null, data.phone(), null
        );
    }
}
