package br.com.holding.payments.integration.asaas.client;

import br.com.holding.payments.integration.asaas.dto.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class AsaasPaymentClient {

    private final AsaasClientFactory clientFactory;
    private final Counter errorCounter;

    public AsaasPaymentClient(AsaasClientFactory clientFactory, MeterRegistry meterRegistry) {
        this.clientFactory = clientFactory;
        this.errorCounter = Counter.builder("asaas_api_errors_total")
                .tag("resource", "payment")
                .register(meterRegistry);
    }

    @Retry(name = "asaas")
    @CircuitBreaker(name = "asaas", fallbackMethod = "handleError")
    public AsaasPaymentResponse create(Long companyId, AsaasPaymentRequest request) {
        RestClient client = clientFactory.createForCompany(companyId);
        return client.post()
                .uri("/payments")
                .body(request)
                .retrieve()
                .body(AsaasPaymentResponse.class);
    }

    @Retry(name = "asaas")
    @CircuitBreaker(name = "asaas", fallbackMethod = "handleError")
    public AsaasPaymentResponse getById(Long companyId, String asaasId) {
        RestClient client = clientFactory.createForCompany(companyId);
        return client.get()
                .uri("/payments/{id}", asaasId)
                .retrieve()
                .body(AsaasPaymentResponse.class);
    }

    @Retry(name = "asaas")
    @CircuitBreaker(name = "asaas", fallbackMethod = "handleError")
    public AsaasPaymentResponse cancel(Long companyId, String asaasId) {
        RestClient client = clientFactory.createForCompany(companyId);
        return client.delete()
                .uri("/payments/{id}", asaasId)
                .retrieve()
                .body(AsaasPaymentResponse.class);
    }

    @Retry(name = "asaas")
    @CircuitBreaker(name = "asaas", fallbackMethod = "handleError")
    public AsaasPaymentResponse refund(Long companyId, String asaasId, AsaasRefundRequest request) {
        RestClient client = clientFactory.createForCompany(companyId);
        return client.post()
                .uri("/payments/{id}/refund", asaasId)
                .body(request)
                .retrieve()
                .body(AsaasPaymentResponse.class);
    }

    @Retry(name = "asaas")
    @CircuitBreaker(name = "asaas", fallbackMethod = "handleError")
    public AsaasPixQrCodeResponse getPixQrCode(Long companyId, String asaasId) {
        RestClient client = clientFactory.createForCompany(companyId);
        return client.get()
                .uri("/payments/{id}/pixQrCode", asaasId)
                .retrieve()
                .body(AsaasPixQrCodeResponse.class);
    }

    @Retry(name = "asaas")
    @CircuitBreaker(name = "asaas", fallbackMethod = "handleError")
    public AsaasBoletoResponse getBoletoIdentificationField(Long companyId, String asaasId) {
        RestClient client = clientFactory.createForCompany(companyId);
        return client.get()
                .uri("/payments/{id}/identificationField", asaasId)
                .retrieve()
                .body(AsaasBoletoResponse.class);
    }

    @Retry(name = "asaas")
    @CircuitBreaker(name = "asaas", fallbackMethod = "handleError")
    public AsaasPageResponse<AsaasPaymentResponse> list(Long companyId, int offset, int limit) {
        RestClient client = clientFactory.createForCompany(companyId);
        return client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/payments")
                        .queryParam("offset", offset)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    @Retry(name = "asaas")
    @CircuitBreaker(name = "asaas", fallbackMethod = "handleError")
    public AsaasPageResponse<AsaasPaymentResponse> listBySubscription(Long companyId, String subscriptionId) {
        RestClient client = clientFactory.createForCompany(companyId);
        return client.get()
                .uri("/subscriptions/{id}/payments", subscriptionId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    private <T> T handleError(Long companyId, Throwable throwable) {
        errorCounter.increment();
        log.error("Asaas Payment API call failed for company {}: {}", companyId, throwable.getMessage());
        throw new AsaasApiException(503, "Asaas Payment API indisponivel: " + throwable.getMessage());
    }

    private <T> T handleError(Long companyId, String id, Throwable throwable) {
        return handleError(companyId, throwable);
    }

    private <T> T handleError(Long companyId, AsaasPaymentRequest req, Throwable throwable) {
        return handleError(companyId, throwable);
    }

    private <T> T handleError(Long companyId, String id, AsaasRefundRequest req, Throwable throwable) {
        return handleError(companyId, throwable);
    }

    private <T> T handleError(Long companyId, int offset, int limit, Throwable throwable) {
        return handleError(companyId, throwable);
    }
}
