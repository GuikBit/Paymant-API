package br.com.holding.payments.integration.asaas.client;

import br.com.holding.payments.company.AsaasEnvironment;
import br.com.holding.payments.company.CompanyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AsaasClientFactory {

    private final CompanyService companyService;
    private final ObjectMapper objectMapper;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public AsaasClientFactory(CompanyService companyService,
                              ObjectMapper objectMapper,
                              @Value("${app.asaas.connect-timeout-ms:3000}") int connectTimeoutMs,
                              @Value("${app.asaas.read-timeout-ms:10000}") int readTimeoutMs) {
        this.companyService = companyService;
        this.objectMapper = objectMapper;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public RestClient createForCompany(Long companyId) {
        String apiKey = companyService.getDecryptedApiKey(companyId);
        AsaasEnvironment env = companyService.getAsaasEnvironment(companyId);

        String baseUrl = env == AsaasEnvironment.PRODUCTION
                ? "https://api.asaas.com/v3"
                : "https://sandbox.asaas.com/api/v3";

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        AsaasErrorHandler errorHandler = new AsaasErrorHandler(objectMapper);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("access_token", apiKey)
                .defaultHeader("Content-Type", "application/json");

        return errorHandler.configure(builder).build();
    }
}
