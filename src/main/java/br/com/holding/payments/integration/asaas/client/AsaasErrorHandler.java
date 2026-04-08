package br.com.holding.payments.integration.asaas.client;

import br.com.holding.payments.integration.asaas.dto.AsaasErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
public class AsaasErrorHandler {

    private final ObjectMapper objectMapper;

    public AsaasErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RestClient.Builder configure(RestClient.Builder builder) {
        return builder.defaultStatusHandler(
                statusCode -> statusCode.isError(),
                (request, response) -> {
                    int statusCode = response.getStatusCode().value();
                    String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);

                    log.warn("Asaas API error: status={}, body={}", statusCode, body);

                    List<AsaasApiException.ErrorDetail> errors = parseErrors(body);
                    String message = buildMessage(statusCode, errors, body);

                    throw new AsaasApiException(statusCode, message, errors);
                }
        );
    }

    private List<AsaasApiException.ErrorDetail> parseErrors(String body) {
        try {
            AsaasErrorResponse errorResponse = objectMapper.readValue(body, AsaasErrorResponse.class);
            if (errorResponse.errors() != null) {
                return errorResponse.errors().stream()
                        .map(e -> new AsaasApiException.ErrorDetail(e.code(), e.description()))
                        .toList();
            }
        } catch (Exception e) {
            log.debug("Could not parse Asaas error response: {}", e.getMessage());
        }
        return List.of();
    }

    private String buildMessage(int statusCode, List<AsaasApiException.ErrorDetail> errors, String rawBody) {
        if (!errors.isEmpty()) {
            return "Asaas API error (%d): %s".formatted(statusCode,
                    errors.stream()
                            .map(AsaasApiException.ErrorDetail::description)
                            .reduce((a, b) -> a + "; " + b)
                            .orElse("Unknown error"));
        }
        return "Asaas API error (%d): %s".formatted(statusCode, rawBody);
    }
}
