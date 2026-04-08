package br.com.holding.payments.integration.asaas.client;

import br.com.holding.payments.integration.asaas.dto.AsaasCustomerRequest;
import br.com.holding.payments.integration.asaas.dto.AsaasCustomerResponse;
import br.com.holding.payments.integration.asaas.dto.AsaasPageResponse;
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
public class AsaasCustomerClient {

    private final AsaasClientFactory clientFactory;
    private final Counter errorCounter;

    public AsaasCustomerClient(AsaasClientFactory clientFactory, MeterRegistry meterRegistry) {
        this.clientFactory = clientFactory;
        this.errorCounter = Counter.builder("asaas_api_errors_total")
                .tag("resource", "customer")
                .register(meterRegistry);
    }

    @Retry(name = "asaas")
    @CircuitBreaker(name = "asaas", fallbackMethod = "handleError")
    public AsaasCustomerResponse create(Long companyId, AsaasCustomerRequest request) {
        RestClient client = clientFactory.createForCompany(companyId);
        return client.post()
                .uri("/customers")
                .body(request)
                .retrieve()
                .body(AsaasCustomerResponse.class);
    }

    @Retry(name = "asaas")
    @CircuitBreaker(name = "asaas", fallbackMethod = "handleError")
    public AsaasCustomerResponse getById(Long companyId, String asaasId) {
        RestClient client = clientFactory.createForCompany(companyId);
        return client.get()
                .uri("/customers/{id}", asaasId)
                .retrieve()
                .body(AsaasCustomerResponse.class);
    }

    @Retry(name = "asaas")
    @CircuitBreaker(name = "asaas", fallbackMethod = "handleError")
    public AsaasCustomerResponse update(Long companyId, String asaasId, AsaasCustomerRequest request) {
        RestClient client = clientFactory.createForCompany(companyId);
        return client.put()
                .uri("/customers/{id}", asaasId)
                .body(request)
                .retrieve()
                .body(AsaasCustomerResponse.class);
    }

    @Retry(name = "asaas")
    @CircuitBreaker(name = "asaas", fallbackMethod = "handleError")
    public AsaasPageResponse<AsaasCustomerResponse> list(Long companyId, int offset, int limit) {
        RestClient client = clientFactory.createForCompany(companyId);
        return client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/customers")
                        .queryParam("offset", offset)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    private <T> T handleError(Long companyId, Throwable throwable) {
        errorCounter.increment();
        log.error("Asaas Customer API call failed for company {}: {}", companyId, throwable.getMessage());
        throw new AsaasApiException(503, "Asaas Customer API indisponivel: " + throwable.getMessage());
    }

    private <T> T handleError(Long companyId, String asaasId, Throwable throwable) {
        return handleError(companyId, throwable);
    }

    private <T> T handleError(Long companyId, String asaasId, AsaasCustomerRequest request, Throwable throwable) {
        return handleError(companyId, throwable);
    }

    private <T> T handleError(Long companyId, AsaasCustomerRequest request, Throwable throwable) {
        return handleError(companyId, throwable);
    }

    private <T> T handleError(Long companyId, int offset, int limit, Throwable throwable) {
        return handleError(companyId, throwable);
    }
}
