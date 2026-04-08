package br.com.holding.payments.integration.asaas.client;

import lombok.Getter;

import java.util.List;

@Getter
public class AsaasApiException extends RuntimeException {

    private final int statusCode;
    private final List<ErrorDetail> errors;

    public AsaasApiException(int statusCode, String message, List<ErrorDetail> errors) {
        super(message);
        this.statusCode = statusCode;
        this.errors = errors != null ? errors : List.of();
    }

    public AsaasApiException(int statusCode, String message) {
        this(statusCode, message, List.of());
    }

    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }

    public boolean isServerError() {
        return statusCode >= 500;
    }

    public record ErrorDetail(String code, String description) {}
}
