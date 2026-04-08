package br.com.holding.payments.integration.asaas.client;

import br.com.holding.payments.integration.asaas.dto.AsaasInstallmentResponse;
import br.com.holding.payments.integration.asaas.dto.AsaasPageResponse;
import br.com.holding.payments.integration.asaas.dto.AsaasPaymentResponse;
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
public class AsaasInstallmentClient {

    private final AsaasClientFactory clientFactory;
    private final Counter errorCounter;

    public AsaasInstallmentClient(AsaasClientFactory clientFactory, MeterRegistry meterRegistry) {
        this.clientFactory = clientFactory;
        this.errorCounter = Counter.builder("asaas_api_errors_total")
                .tag("resource", "installment")
                .register(meterRegistry);
    }

    @Retry(name = "asaas")
    @CircuitBreaker(name = "asaas", fallbackMethod = "handleError")
    public AsaasInstallmentResponse getById(Long companyId, String asaasId) {
        RestClient client = clientFactory.createForCompany(companyId);
        return client.get()
                .uri("/installments/{id}", asaasId)
                .retrieve()
                .body(AsaasInstallmentResponse.class);
    }

    @Retry(name = "asaas")
    @CircuitBreaker(name = "asaas", fallbackMethod = "handleError")
    public AsaasPageResponse<AsaasPaymentResponse> listPayments(Long companyId, String installmentId) {
        RestClient client = clientFactory.createForCompany(companyId);
        return client.get()
                .uri("/installments/{id}/payments", installmentId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    @Retry(name = "asaas")
    @CircuitBreaker(name = "asaas", fallbackMethod = "handleError")
    public void cancel(Long companyId, String asaasId) {
        RestClient client = clientFactory.createForCompany(companyId);
        client.delete()
                .uri("/installments/{id}", asaasId)
                .retrieve()
                .toBodilessEntity();
    }

    private <T> T handleError(Long companyId, Throwable throwable) {
        errorCounter.increment();
        log.error("Asaas Installment API call failed for company {}: {}", companyId, throwable.getMessage());
        throw new AsaasApiException(503, "Asaas Installment API indisponivel: " + throwable.getMessage());
    }

    private <T> T handleError(Long companyId, String id, Throwable throwable) {
        return handleError(companyId, throwable);
    }
}
