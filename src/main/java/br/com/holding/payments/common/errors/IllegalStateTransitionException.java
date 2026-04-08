package br.com.holding.payments.common.errors;

public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(Object entityId, Object currentStatus, Object targetStatus) {
        super("Cannot transition entity %s from %s to %s".formatted(entityId, currentStatus, targetStatus));
    }
}
