package br.com.holding.payments.common.errors;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String entity, Object id) {
        super("%s not found with id: %s".formatted(entity, id));
    }
}
