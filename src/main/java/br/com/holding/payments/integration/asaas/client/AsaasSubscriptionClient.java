package br.com.holding.payments.integration.asaas.client;

import br.com.holding.payments.integration.asaas.dto.AsaasPageResponse;
import br.com.holding.payments.integration.asaas.dto.AsaasSubscriptionRequest;
import br.com.holding.payments.integration.asaas.dto.AsaasSubscriptionResponse;
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
public class AsaasSubscriptionClient {

    private final AsaasClientFactory clientFactory;
    private final Counter errorCounter;

    public AsaasSubscriptionClient(AsaasClientFactory clientFactory, MeterRegistry meterRegistry) {
        this.clientFactory = clientFactory;
        this.errorCounter = Counter.builder("asaas_api_errors_total")
                .tag("resource", "subscription")
                .register(meterRegistry);
    }

    @Retry(name = "asaas")
    @CircuitBreaker(name = "asaas", fallbackMethod = "handleError")
    public AsaasSubscriptionResponse create(Long companyId, AsaasSubscriptionRequest request) {
        RestClient client = clientFactory.createForCompany(companyId);
        return client.post()
                .uri("/subscriptions")
                .body(request)
                .retrieve()
                .body(AsaasSubscriptionResponse.class);
    }

    @Retry(name = "asaas")
    @CircuitBreaker(name = "asaas", fallbackMethod = "handleError")
    public AsaasSubscriptionResponse getById(Long companyId, String asaasId) {
        RestClient client = clientFactory.createForCompany(companyId);
        return client.get()
                .uri("/subscriptions/{id}", asaasId)
                .retrieve()
                .body(AsaasSubscriptionResponse.class);
    }

    @Retry(name = "asaas")
    @CircuitBreaker(name = "asaas", fallbackMethod = "handleError")
    public AsaasSubscriptionResponse cancel(Long companyId, String asaasId) {
        RestClient client = clientFactory.createForCompany(companyId);
        return client.delete()
                .uri("/subscriptions/{id}", asaasId)
                .retrieve()
                .body(AsaasSubscriptionResponse.class);
    }

    @Retry(name = "asaas")
    @CircuitBreaker(name = "asaas", fallbackMethod = "handleError")
    public AsaasPageResponse<AsaasSubscriptionResponse> list(Long companyId, int offset, int limit) {
        RestClient client = clientFactory.createForCompany(companyId);
        return client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/subscriptions")
                        .queryParam("offset", offset)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    private <T> T handleError(Long companyId, Throwable throwable) {
        errorCounter.increment();
        log.error("Asaas Subscription API call failed for company {}: {}", companyId, throwable.getMessage());
        throw new AsaasApiException(503, "Asaas Subscription API indisponivel: " + throwable.getMessage());
    }

    private <T> T handleError(Long companyId, String id, Throwable throwable) {
        return handleError(companyId, throwable);
    }

    private <T> T handleError(Long companyId, AsaasSubscriptionRequest req, Throwable throwable) {
        return handleError(companyId, throwable);
    }

    private <T> T handleError(Long companyId, int offset, int limit, Throwable throwable) {
        return handleError(companyId, throwable);
    }
}
